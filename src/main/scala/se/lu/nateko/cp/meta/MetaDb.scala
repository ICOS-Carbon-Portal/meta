package se.lu.nateko.cp.meta

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.semanticweb.owlapi.apibinding.OWLManager
import se.lu.nateko.cp.meta.api.{RdfLens, RdfLenses, SparqlServer}
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, flattenToSeq}
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Extractor, Ingester, Ingestion, StatementProvider}
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, LoggingInstanceServer, Rdf4jInstanceServer, TriplestoreConnection, WriteNotifyingInstanceServer}
import se.lu.nateko.cp.meta.onto.{InstOnto, Onto}
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.citation.CitationClient.{CitationCache, DoiCache}
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.labeling.StationLabelingService
import se.lu.nateko.cp.meta.services.linkeddata.{Rdf4jUriSerializer, UriSerializer}
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}
import se.lu.nateko.cp.meta.services.upload.etc.EtcUploadTransformer
import se.lu.nateko.cp.meta.services.upload.{DataObjectInstanceServers, StaticObjectReader, UploadService}
import se.lu.nateko.cp.meta.services.{FileStorageService, Rdf4jSparqlRunner, ServiceException}
import se.lu.nateko.cp.meta.utils.async.ok
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf

import java.net.URI
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class MetaDb (
	val instanceServers: Map[String, InstanceServer],
	val instOntos: Map[String, InstOnto],
	val uploadService: UploadService,
	val labelingService: Option[StationLabelingService],
	val fileService: FileStorageService,
	val sparql: SparqlServer,
	val magicRepo: SailRepository,
	val citer: CitationProvider,
	val config: CpmetaConfig
)(using Materializer, EnvriConfigs)(using system: ActorSystem) extends AutoCloseable:

	export uploadService.servers.{vocab, metaVocab, lenses}
	private val log = Logging.getLogger(system, this)
	def metaReader: StaticObjectReader = citer.metaReader
	def vanillaRepo: Repository = citer.repo
	def vanillaGlob: InstanceServer = citer.server

	val uriSerializer: UriSerializer =
		new Rdf4jUriSerializer(vanillaRepo, vocab, metaVocab, lenses, citer.doiCiter, config)

	def makeReadonlyDumpIndexAndCaches(msg: String): Future[String] =
		magicRepo.getSail match
			case cp: CpNotifyingSail =>
				val exe = summon[ActorSystem].dispatcher
				cp.makeReadonlyDumpIndexAndCaches(msg)(using exe)
			case _ => Future.successful("Not a Carbon Portal's \"magic\" repository, cannot switch to read-only mode")

	def initSparqlMagicIndex(idxData: Option[IndexData]): Future[Done] =
		magicRepo.getSail match
			case cp: CpNotifyingSail => cp.initSparqlMagicIndex(idxData)
			case _ =>
				log.info("Magic SPARQL index is disabled, skipping its initialization")
				ok


	override def close(): Unit =
		sparql.shutdown()
		for((_, server) <- instanceServers) server.shutDown()
		magicRepo.shutDown()

end MetaDb

object MetaDb:
	def getAllInstanceServerConfigs(confs: InstanceServersConfig): Map[String, InstanceServerConfig] = {
		confs.specific ++ confs.forDataObjects.values.flatMap{dataObjServers =>
			dataObjServers.definitions.map{ servDef =>
				val writeCtxt = getInstServerContext(dataObjServers, servDef)
				servDef.label -> InstanceServerConfig(
					logName = Some(servDef.label),
					skipLogIngestionAtStart = servDef.replayLogFrom.map(_ => false),
					logIngestionFromId = servDef.replayLogFrom,
					readContexts = Some(dataObjServers.commonReadContexts :+ writeCtxt),
					writeContext = writeCtxt,
					ingestion = None
				)
			}
		}
	}

	def getInstServerContext(conf: DataObjectInstServersConfig, servDef: DataObjectInstServerDefinition) =
		new java.net.URI(conf.uriPrefix.toString + servDef.label + "/")

	def getLenses(servConf: InstanceServersConfig, uplConf: UploadServiceConfig): RdfLenses =

		def confsToLenses[L](confs: Map[Envri, String], factory: (URI, Seq[URI]) => L): Map[Envri, L] = confs
			.flatMap: (envri, instServId) =>
				servConf.specific.get(instServId).map: conf =>
					val readContexts = conf.readContexts.getOrElse(Seq(conf.writeContext))
					(
						envri,
						factory(conf.writeContext, readContexts)
					)

		val perFormat = servConf.forDataObjects.map: (envri, conf) =>
			envri -> conf
				.definitions
				.map[(URI, RdfLens.DobjLens)]: doisd =>
					val writeCtxt = getInstServerContext(conf, doisd)
					val readCtxts = writeCtxt +: conf.commonReadContexts
					doisd.format -> RdfLens.dobjLens(writeCtxt, readCtxts)
				.toMap

		val cpOwn = servConf.metaFlow.flattenToSeq
			.flatMap: flConf =>
				val servId = flConf.cpMetaInstanceServerId
				servConf.specific.get(servId).map[(String, RdfLens.CpLens)]: conf =>
					val readContexts = conf.readContexts.getOrElse(Seq(conf.writeContext))
					servId -> RdfLens.cpLens(conf.writeContext, readContexts)
			.toMap

		RdfLenses(
			metaInstances = confsToLenses(uplConf.metaServers, RdfLens.metaLens),
			cpMetaInstances = cpOwn,
			collections = confsToLenses(uplConf.collectionServers, RdfLens.collLens),
			documents = confsToLenses(uplConf.documentServers, RdfLens.docLens),
			dobjPerFormat = perFormat
		)
	end getLenses
end MetaDb

class MetaDbFactory(using system: ActorSystem, mat: Materializer):
	import MetaDb.*

	private val log = Logging.getLogger(system, this)
	private given ExecutionContext = system.dispatcher

	def apply(citCache: CitationCache, metaCache: DoiCache, config0: CpmetaConfig): Future[MetaDb] =

		validateConfig(config0)

		val (isFreshInit, baseSail) = StorageSail.apply(config0.rdfStorage)
		val citer = CitationProvider(baseSail, citCache, metaCache, config0)

		val noMagic = isFreshInit || config0.rdfStorage.disableCpIndex

		val idxFactories = if noMagic then None else
			val indexHandler = IndexHandler(system.scheduler)
			val geoProvider = new GeoIndexProvider(using ExecutionContext.global)
			Some(indexHandler -> geoProvider)

		val sail = CpNotifyingSail(baseSail, idxFactories, citer)
		val repo = new SailRepository(sail)
		repo.init()

		val config: CpmetaConfig = if isFreshInit
			then config0.copy(rdfStorage = config0.rdfStorage.copy(recreateAtStartup = true))
			else config0

		val ontosFut = Future{makeOntos(config.onto.ontologies)}.andThen:
			case _ => log.info("ontology servers created")

		given EnvriConfigs = config.core.envriConfigs

		val serversFut =
			//NativeStore crashes under unrestrained parallel write conditions
			val singleThreadExe = if config.rdfStorage.lmdb.isEmpty || isFreshInit then
				val ctxt = Executors.newSingleThreadExecutor()
				Some(ExecutionContext.fromExecutorService(ctxt))
			else None

			/*
			 LMDB seems to work fine under fully parallel write conditions,
			 with the exception of full re-building of the triplestore database from rdflog.
			 Note that in the typical meta service restart scenario RDF storage is not re-built,
			 but multi-threaded initialization can be beneficial for startup time, because some
			 of RDF-graph-init jobs are expensive and are best run as background even after the
			 server starts up (see `enum IngestionMode` in CpmetaConfig.scala)
			*/
			given ExecutionContext = singleThreadExe.getOrElse(ExecutionContext.global)

			makeInstanceServers(repo, Ingestion.allProviders, config).andThen:
				case _ =>
					log.info("instance servers created")
					singleThreadExe.foreach(_.shutdown())


		for instanceServers <- serversFut; ontos <- ontosFut yield
			log.info("both instance servers and onto servers created")
			val instOntos = config.onto.instOntoServers.map{
				case (servId, servConf) =>
					val instServer = instanceServers(servConf.instanceServerId)
					val onto = ontos(servConf.ontoId)
					(servId, new InstOnto(instServer, onto))
			}

			val uploadService = makeUploadService(citer, instanceServers, config)

			val fileService = new FileStorageService(new java.io.File(config.fileStoragePath))

			val labelingService = config.stationLabelingService.map{ conf =>
				val onto = ontos(conf.ontoId)
				val metaVocab = citer.metaVocab
				new StationLabelingService(instanceServers, onto, fileService, metaVocab, conf)
			}

			val sparqlServer = new Rdf4jSparqlServer(repo, config.sparql)

			val db = new MetaDb(instanceServers, instOntos, uploadService, labelingService, fileService, sparqlServer, repo, citer, config)
			if isFreshInit then sail.makeReadonly("This was a fresh RDF store initialization, running in " +
				"readonly mode; restart the server for proper operation")
			db
		end for
	end apply

	private def makeUploadService(
		citationProvider: CitationProvider,
		instanceServers: Map[String, InstanceServer],
		config: CpmetaConfig
	): UploadService = {
		val metaServers = config.dataUploadService.metaServers.view.mapValues(instanceServers.apply).toMap
		val collectionServers = config.dataUploadService.collectionServers.view.mapValues(instanceServers.apply).toMap
		val vanillaGlob: InstanceServer = citationProvider.server
		given factory: ValueFactory = vanillaGlob.factory
		given EnvriConfigs = config.core.envriConfigs

		val perFormatServers: Map[Envri, Map[IRI, InstanceServer]] = config.instanceServers.forDataObjects.map{
			case (envri, dobjServConfs) => envri -> dobjServConfs.definitions.map{ servDef =>
				servDef.format.toRdf -> instanceServers(servDef.label)
			}.toMap
		}

		val docInstServs = config.dataUploadService.documentServers.map{case (envri, servId) =>
			envri -> instanceServers(servId)
		}

		val uploadConf = config.dataUploadService

		val vanillaRepo = citationProvider.repo
		val sparqlRunner = new Rdf4jSparqlRunner(vanillaRepo)

		val dataObjServers = new DataObjectInstanceServers(vanillaGlob, citationProvider, metaServers, collectionServers, docInstServs, perFormatServers)
		val etcHelper = new EtcUploadTransformer(sparqlRunner, uploadConf.etc, dataObjServers.vocab)

		new UploadService(dataObjServers, etcHelper, uploadConf)
	}

	private def makeInstanceServer(initRepo: Repository, conf: InstanceServerConfig, globConf: CpmetaConfig): InstanceServer =

		given factory: ValueFactory = initRepo.getValueFactory

		val writeContext = conf.writeContext.toRdf
		val readContexts = conf.readContexts.fold(Seq(writeContext))(_.map(_.toRdf))

		conf.logName match
			case Some(logName) =>
				val rdfLog = PostgresRdfLog(logName, globConf.rdfLog, factory)

				if !conf.skipLogIngestionAtStart.getOrElse(!globConf.rdfStorage.recreateAtStartup)
				then
					val cleanFirst = if(conf.logIngestionFromId.isDefined) false else true
					val msgDetail = conf.logIngestionFromId.fold("")(id => s"starting from id $id ")
					log.info(s"Ingesting from RDF log $logName $msgDetail...")
					val updates = conf.logIngestionFromId.fold(rdfLog.updates)(rdfLog.updatesFromId)
					RdfUpdateLogIngester.ingest(updates, initRepo, cleanFirst, writeContext)
					log.info(s"Ingesting from RDF log $logName done!")

				val rdf4jServer = new Rdf4jInstanceServer(initRepo, readContexts, writeContext)
				new LoggingInstanceServer(rdf4jServer, rdfLog)

			case None =>
				new Rdf4jInstanceServer(initRepo, readContexts, writeContext)
		end match

	end makeInstanceServer

	private def makeOntos(confs: Seq[SchemaOntologyConfig]): Map[String, Onto] = {
		val owlManager = OWLManager.createOWLOntologyManager

		confs.foldLeft(Map.empty[String, Onto])((acc, conf) => {
			val owl = utils.owlapi.getOntologyFromJarResourceFile(conf.owlResource, owlManager)

			conf.ontoId match{
				case None => acc
				case Some(ontId) => acc + ((ontId, new Onto(owl)))
			}
		})
	}

	private def validateConfig(config: CpmetaConfig): Unit = {

		val dobjInstServKeys = (
			config.instanceServers.forDataObjects.values.flatMap(_.definitions.map(_.label)) ++
			config.instanceServers.specific.keys
		).toList

		val dups = dobjInstServKeys.groupBy(identity).collect{ case (s, l) if l.lengthCompare(1) > 0 => s}
		if(!dups.isEmpty){
			throw new ServiceException(s"Duplicate instance server key(s) in the config: ${dups.mkString(", ")}")
		}
		val instServerIds = config.instanceServers.specific.keys.toSet
		val schemaOntIds = config.onto.ontologies.map(_.ontoId).flatten.toSet

		def ensureInstServerExists(instanceServerId: String): Unit =
			if(!instServerIds.contains(instanceServerId))
				throw new ServiceException(s"Missing instance server with id '$instanceServerId'. Check your config.")

		def ensureSchemaOntExists(schemaOntId: String): Unit =
			if(!schemaOntIds.contains(schemaOntId))
				throw new ServiceException(s"Missing schema ontology with id '$schemaOntId'. Check your config.")

		config.dataUploadService.metaServers.values.foreach(ensureInstServerExists)
		config.dataUploadService.collectionServers.values.foreach(ensureInstServerExists)

		config.onto.instOntoServers.values.foreach{ conf =>
			ensureInstServerExists(conf.instanceServerId)
			ensureSchemaOntExists(conf.ontoId)
		}
	}

	/**
	 * Includes support for dependencies when initializing InstanceServers.
	 * Namely, ingestion can now wait until certain other InstanceServers have
	 * been completely initialized from their RDF logs and other ingesters.
	 */
	private def makeInstanceServers(
		repo: Repository,
		providersFactory: => Map[String, StatementProvider],
		config: CpmetaConfig
	)(using ExecutionContext): Future[Map[String, InstanceServer]] =

		given BnodeStabilizers = new BnodeStabilizers
		val instServerConfs = getAllInstanceServerConfigs(config.instanceServers)
		val valueFactory = repo.getValueFactory
		lazy val providers = providersFactory

		type ServerFutures = Map[String, Future[InstanceServer]]

		def makeNextServer(acc: ServerFutures, id: String): ServerFutures = if(acc.contains(id)) acc else
			val servConf: InstanceServerConfig = instServerConfs(id)
			import IngestionMode.{EAGER, BACKGROUND}

			val basicInit: Future[InstanceServer] =
				val init = Future(makeInstanceServer(repo, servConf, config))

				if
					config.instanceServers.metaFlow.flattenToSeq.collect{
						case icos: IcosMetaFlowConfig => icos.otcMetaInstanceServerId
					}.contains(id)
				then init.map(new WriteNotifyingInstanceServer(_))
				else init

			servConf.ingestion match{

				case Some(IngestionConfig(ingesterId, waitFor, mode @ (EAGER | BACKGROUND))) =>

					val (withDependencies, dependenciesDone): (ServerFutures, Future[Unit]) = waitFor match {
						case None =>
							(acc, Future.successful(()))
						case Some(depIds) =>
							val withDeps = depIds.foldLeft(acc)(makeNextServer)
							val done = Future.sequence(depIds.map(withDeps.apply)).map(_ => ())
							(withDeps, done)
					}

					val afterIngestion = for
						server <- basicInit
						_ <- dependenciesDone
						_ <- {
							val ingestFut = (providers(ingesterId) match
								case ingester: Ingester => Ingestion.ingest(server, ingester, valueFactory)
								case extractor: Extractor => Ingestion.ingest(server, extractor, repo)
							).andThen:
								case Success(_) => log.info("ingestion done for " + id)
								case Failure(err) => log.error(s"error while ingesting for $id", err)

							if mode == EAGER then ingestFut else
								log.info(s"ingestion for $id has been started in the background")
								Future.successful(0)
						}
					yield server

					if mode == EAGER || !dependenciesDone.isCompleted then
						log.info("ingestion scheduled for " + id)
					withDependencies + (id -> afterIngestion)
				case _ =>
					acc + (id -> basicInit)
			}
		end makeNextServer

		val futures: ServerFutures = instServerConfs.keys.foldLeft[ServerFutures](Map.empty)(makeNextServer)
		Future.sequence(futures.map{case (id, fut) => fut.map((id, _))}).map(_.toMap)
	end makeInstanceServers

end MetaDbFactory
