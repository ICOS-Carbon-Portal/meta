package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.api.{RdfLens, RdfLenses, SparqlRunner, SparqlServer}
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, flattenToSeq}
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Extractor, Ingester, Ingestion, StatementProvider}
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, LoggingInstanceServer, QleverInstanceServer, TriplestoreConnection, WriteNotifyingInstanceServer}
import se.lu.nateko.cp.meta.onto.{InstOnto, Onto}
import se.lu.nateko.cp.meta.persistence.QleverUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.citation.CitationClient.{CitationCache, DoiCache}
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.labeling.StationLabelingService
import se.lu.nateko.cp.meta.services.linkeddata.{Rdf4jUriSerializer, UriSerializer}
import se.lu.nateko.cp.meta.services.sparql.{QleverClient, QleverSparqlServer}
import se.lu.nateko.cp.meta.services.upload.etc.EtcUploadTransformer
import se.lu.nateko.cp.meta.services.upload.{DataObjectInstanceServers, StaticObjectReader, UploadService}
import se.lu.nateko.cp.meta.services.{FileStorageService, QleverSparqlRunner, ServiceException}

import se.lu.nateko.cp.meta.utils.rdf4j.toRdf

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}


class MetaDb(
	val instanceServers: Map[String, InstanceServer],
	val instOntos: Map[String, InstOnto],
	val uploadService: UploadService,
	val labelingService: Option[StationLabelingService],
	val fileService: FileStorageService,
	val sparql: SparqlServer,
	val sparqlRunner: SparqlRunner,
	val citer: CitationProvider,
	val config: CpmetaConfig,
	val dropTripleObjects: (IRI, IRI, Seq[IRI]) => Try[Unit]
)(using Materializer, EnvriConfigs)(using system: ActorSystem) extends AutoCloseable:

	export uploadService.servers.{vocab, metaVocab, lenses}
	private val log = Logging.getLogger(system, this)
	def metaReader: StaticObjectReader = citer.metaReader
	def vanillaGlob: InstanceServer = citer.server

	val uriSerializer: UriSerializer =
		new Rdf4jUriSerializer(vanillaGlob, sparqlRunner, vocab, metaVocab, lenses, citer.doiCiter, config)

	def makeReadonlyDumpIndexAndCaches(msg: String): Future[String] =
		Future.successful("QLever backend: read-only mode switching not supported")

	override def close(): Unit =
		sparql.shutdown()
		for((_, server) <- instanceServers) server.shutDown()

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

	def apply(citCache: CitationCache, metaCache: DoiCache, config: CpmetaConfig): Future[MetaDb] =

		validateConfig(config)

		val qleverClient = new QleverClient(config.qlever)

		// Global InstanceServer (reads across all graphs, no write context) for CitationProvider
		val globalServer = new QleverInstanceServer(qleverClient, Nil, null)
		val citer = CitationProvider(globalServer, citCache, metaCache, config)

		given EnvriConfigs = config.core.envriConfigs

		val ontosFut = Future{makeOntos(config.onto.ontologies)}.andThen:
			case _ => log.info("ontology servers created")

		val serversFut =
			given ExecutionContext = ExecutionContext.global

			makeInstanceServers(qleverClient, Ingestion.allProviders, config).andThen:
				case _ => log.info("instance servers created")

		for instanceServers <- serversFut; ontos <- ontosFut yield
			log.info("both instance servers and onto servers created")
			val instOntos = config.onto.instOntoServers.map{
				case (servId, servConf) =>
					val instServer = instanceServers(servConf.instanceServerId)
					val onto = ontos(servConf.ontoId)
					(servId, new InstOnto(instServer, onto))
			}

			val sparqlRunner = new QleverSparqlRunner(qleverClient)
			val uploadService = makeUploadService(citer, instanceServers, sparqlRunner, config)

			val fileService = new FileStorageService(new java.io.File(config.fileStoragePath))

			val labelingService = config.stationLabelingService.map{ conf =>
				val onto = ontos(conf.ontoId)
				val metaVocab = citer.metaVocab
				new StationLabelingService(instanceServers, onto, fileService, metaVocab, conf)
			}

			val sparqlServer = new QleverSparqlServer(qleverClient, config.sparql)

			val dropTripleObjs = makeDropTripleObjects(qleverClient)

			new MetaDb(instanceServers, instOntos, uploadService, labelingService, fileService, sparqlServer, sparqlRunner, citer, config, dropTripleObjs)
		end for
	end apply

	private def makeDropTripleObjects(client: QleverClient): (IRI, IRI, Seq[IRI]) => Try[Unit] =
		(subj, pred, ctxts) => Try:
			import scala.concurrent.Await
			import scala.concurrent.duration.DurationInt
			val deleteStatements = ctxts match
				case Nil =>
					s"DELETE WHERE { ?g <${subj.stringValue}> <${pred.stringValue}> ?o }"
				case _ =>
					ctxts.map: ctx =>
						s"WITH <${ctx.stringValue}> DELETE WHERE { <${subj.stringValue}> <${pred.stringValue}> ?o }"
					.mkString(" ; ")
			Await.result(client.sparqlUpdate(deleteStatements).map(_ => ()), 30.seconds)

	private def makeUploadService(
		citationProvider: CitationProvider,
		instanceServers: Map[String, InstanceServer],
		sparqlRunner: SparqlRunner,
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

		val dataObjServers = new DataObjectInstanceServers(vanillaGlob, citationProvider, metaServers, collectionServers, docInstServs, perFormatServers)
		val etcHelper = new EtcUploadTransformer(sparqlRunner, uploadConf.etc, dataObjServers.vocab)

		new UploadService(dataObjServers, etcHelper, uploadConf)
	}

	private def makeInstanceServer(qleverClient: QleverClient, conf: InstanceServerConfig, globConf: CpmetaConfig): InstanceServer =

		given factory: ValueFactory = SimpleValueFactory.getInstance()

		val writeCtxUri = conf.writeContext
		val writeContext = factory.createIRI(writeCtxUri.toString)
		val readContexts = conf.readContexts.fold(Seq(writeContext))(_.map(uri => factory.createIRI(uri.toString)))

		conf.logName match
			case Some(logName) =>
				val rdfLog = PostgresRdfLog(logName, globConf.rdfLog, factory)

				if !conf.skipLogIngestionAtStart.getOrElse(!globConf.qlever.recreateAtStartup)
				then
					val cleanFirst = conf.logIngestionFromId.isEmpty
					val msgDetail = conf.logIngestionFromId.fold("")(id => s"starting from id $id ")
					log.info(s"Ingesting from RDF log $logName $msgDetail...")
					val updates = conf.logIngestionFromId.fold(rdfLog.updates)(rdfLog.updatesFromId)
					QleverUpdateLogIngester.ingest(updates, qleverClient, cleanFirst, writeContext).get
					log.info(s"Ingesting from RDF log $logName done!")

				val qleverServer = new QleverInstanceServer(qleverClient, readContexts, writeContext)
				new LoggingInstanceServer(qleverServer, rdfLog)

			case None =>
				new QleverInstanceServer(qleverClient, readContexts, writeContext)
		end match

	end makeInstanceServer

	private def makeOntos(confs: Seq[SchemaOntologyConfig]): Map[String, Onto] = {
		val owlManager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager

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

	private def makeInstanceServers(
		qleverClient: QleverClient,
		providersFactory: => Map[String, StatementProvider],
		config: CpmetaConfig
	)(using ExecutionContext): Future[Map[String, InstanceServer]] =

		given BnodeStabilizers = new BnodeStabilizers
		val instServerConfs = getAllInstanceServerConfigs(config.instanceServers)
		val valueFactory = SimpleValueFactory.getInstance()
		lazy val providers = providersFactory

		type ServerFutures = Map[String, Future[InstanceServer]]

		def makeNextServer(acc: ServerFutures, id: String): ServerFutures = if(acc.contains(id)) acc else
			val servConf: InstanceServerConfig = instServerConfs(id)
			import IngestionMode.{EAGER, BACKGROUND}

			val basicInit: Future[InstanceServer] =
				val init = Future(makeInstanceServer(qleverClient, servConf, config))

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
								case ingester: Ingester =>
									Ingestion.ingest(server, ingester, valueFactory)
								case extractor: Extractor =>
									// Extractors require a Repository; use an empty in-memory repo as placeholder
									// TODO: provide QLever-backed repo to extractors
									val emptyRepo = se.lu.nateko.cp.meta.utils.rdf4j.Loading.emptyInMemory
									Ingestion.ingest(server, extractor, emptyRepo)
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
