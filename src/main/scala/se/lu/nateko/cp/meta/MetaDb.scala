package se.lu.nateko.cp.meta

import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Paths

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.semanticweb.owlapi.apibinding.OWLManager

import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.meta.api.SparqlServer
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.ingestion.Extractor
import se.lu.nateko.cp.meta.ingestion.Ingester
import se.lu.nateko.cp.meta.ingestion.Ingestion
import se.lu.nateko.cp.meta.ingestion.StatementProvider
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.FileStorageService
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.ServiceException
import se.lu.nateko.cp.meta.services.labeling.StationLabelingService
import se.lu.nateko.cp.meta.services.linkeddata.Rdf4jUriSerializer
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.sparql.magic.CpNativeStore
import se.lu.nateko.cp.meta.services.upload.{ DataObjectInstanceServers, UploadService, DoiService }
import se.lu.nateko.cp.meta.services.upload.etc.EtcUploadTransformer
import se.lu.nateko.cp.meta.services.citation.CitationProviderFactory
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory
import se.lu.nateko.cp.meta.services.citation.CitationClient
import org.eclipse.rdf4j.sail.Sail


class MetaDb (
	val instanceServers: Map[String, InstanceServer],
	val instOntos: Map[String, InstOnto],
	val uploadService: UploadService,
	val labelingService: StationLabelingService,
	val fileService: FileStorageService,
	val sparql: SparqlServer,
	val repo: Repository,
	val dataCiter: CitationClient,
	val config: CpmetaConfig
)(implicit mat: Materializer, configs: EnvriConfigs, system: ActorSystem) extends AutoCloseable{

	val uriSerializer: UriSerializer = new Rdf4jUriSerializer(repo, uploadService.servers, dataCiter,config)

	override def close(): Unit = {
		sparql.shutdown()
		for((_, server) <- instanceServers) server.shutDown()
		repo.shutDown()
	}

}

object MetaDb{
	def getAllInstanceServerConfigs(confs: InstanceServersConfig): Map[String, InstanceServerConfig] = {
		confs.specific ++ confs.forDataObjects.values.flatMap{dataObjServers =>
			dataObjServers.definitions.map{ servDef =>
				val writeCtxt = getInstServerContext(dataObjServers, servDef)
				servDef.label -> InstanceServerConfig(
					logName = Some(servDef.label),
					skipLogIngestionAtStart = None,
					readContexts = Some(dataObjServers.commonReadContexts :+ writeCtxt),
					writeContexts = Seq(writeCtxt),
					ingestion = None
				)
			}
		}
	}

	def getInstServerContext(conf: DataObjectInstServersConfig, servDef: DataObjectInstServerDefinition) =
		new java.net.URI(conf.uriPrefix.toString + servDef.label + "/")

}

class MetaDbFactory(implicit system: ActorSystem, mat: Materializer) {
	import MetaDb._

	private val log = system.log

	def apply(config0: CpmetaConfig): Future[MetaDb] = {

		validateConfig(config0)
		import system.dispatcher

		val citerFactory = new CitationProviderFactory(config0)
		val (repo, didNotExist, citer) = makeInitRepo(config0, citerFactory)

		val config = if(didNotExist)
				config0.copy(rdfStorage = config0.rdfStorage.copy(recreateAtStartup = true))
			else config0

		val ontosFut = Future{makeOntos(config.onto.ontologies)}

		implicit val envriConfs = config.core.envriConfigs

		val serversFut = {
			val exeServ = java.util.concurrent.Executors.newSingleThreadExecutor
			val ctxt = ExecutionContext.fromExecutorService(exeServ)
			makeInstanceServers(repo, Ingestion.allProviders, config)(ctxt).andThen{
				case _ =>
					ctxt.shutdown()
					log.info("instance servers created")
			}(system.dispatcher)
		}

		for(instanceServers <- serversFut; ontos <-ontosFut) yield{
			val instOntos = config.onto.instOntoServers.map{
				case (servId, servConf) =>
					val instServer = instanceServers(servConf.instanceServerId)
					val onto = ontos(servConf.ontoId)
					(servId, new InstOnto(instServer, onto))
			}

			val uploadService = makeUploadService(config, repo, instanceServers)

			val fileService = new FileStorageService(new java.io.File(config.fileStoragePath))

			val labelingService = {
				val conf = config.stationLabelingService
				val provisional = instanceServers(conf.provisionalInfoInstanceServerId)
				val main = instanceServers(conf.instanceServerId)
				val onto = ontos(conf.ontoId)
				new StationLabelingService(main, provisional, onto, fileService, conf)
			}

			val sparqlServer = new Rdf4jSparqlServer(repo, config.sparql, log)

			new MetaDb(instanceServers, instOntos, uploadService, labelingService, fileService, sparqlServer, repo, citer, config)
		}
	}

	private def makeInitRepo(config: CpmetaConfig, citationFactory: CitationProviderFactory): (Repository, Boolean, CitationClient) = {
		import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler

		val indexInit: Sail => IndexHandler = new IndexHandler(_, system.scheduler, log)(system.dispatcher)
		val native = new CpNativeStore(config.rdfStorage, indexInit, citationFactory, log)

		val repo = new SailRepository(native)
		repo.initialize()
		(repo, native.isFreshInit, native.getCitationClient)
	}

	private def makeUploadService(
		config: CpmetaConfig,
		repo: Repository,
		instanceServers: Map[String, InstanceServer]
	): UploadService = {
		val metaServers = config.dataUploadService.metaServers.view.mapValues(instanceServers.apply).toMap
		val collectionServers = config.dataUploadService.collectionServers.view.mapValues(instanceServers.apply).toMap
		implicit val factory = repo.getValueFactory

		val allDataObjInstServs = config.instanceServers.forDataObjects.map{ case (envri, dobjServConfs) =>
			val readContexts = dobjServConfs.definitions.map(getInstServerContext(dobjServConfs, _))
			val instServConf = InstanceServerConfig(Nil, None, None, Some(readContexts), None)
			envri -> makeInstanceServer(repo, instServConf, config)
		}

		val perFormatServers: Map[Envri, Map[IRI, InstanceServer]] = config.instanceServers.forDataObjects.map{
			case (envri, dobjServConfs) => envri -> dobjServConfs.definitions.map{ servDef =>
				factory.createIRI(servDef.format) -> instanceServers(servDef.label)
			}.toMap
		}

		val docInstServs = config.dataUploadService.documentServers.map{case (envri, servId) =>
			envri -> instanceServers(servId)
		}

		val uploadConf = config.dataUploadService

		val sparqlRunner = new Rdf4jSparqlRunner(repo)
		implicit val envriConfs = config.core.envriConfigs
		val dataObjServers = new DataObjectInstanceServers(metaServers, collectionServers, docInstServs, allDataObjInstServs, perFormatServers)
		val etcHelper = new EtcUploadTransformer(sparqlRunner, uploadConf.etc, dataObjServers.vocab)

		new UploadService(dataObjServers, sparqlRunner, etcHelper, uploadConf)
	}

	private def makeInstanceServer(initRepo: Repository, conf: InstanceServerConfig, globConf: CpmetaConfig): InstanceServer = {

		val factory = initRepo.getValueFactory

		val writeContexts = conf.writeContexts.map(ctxt => factory.createIRI(ctxt))
		val readContexts = conf.readContexts.getOrElse(conf.writeContexts).map(ctxt => factory.createIRI(ctxt))

		conf.logName match{
			case Some(logName) =>
				val rdfLog = PostgresRdfLog(logName, globConf.rdfLog, factory)

				val repo = if(conf.skipLogIngestionAtStart.getOrElse(!globConf.rdfStorage.recreateAtStartup))
						initRepo
					else {
						log.info(s"Ingesting from RDF log $logName ...")
						val res = RdfUpdateLogIngester.ingest(rdfLog.updates, initRepo, true, writeContexts: _*)
						log.info(s"Ingesting from RDF log $logName done!")
						res
					}

				val rdf4jServer = new Rdf4jInstanceServer(repo, readContexts, writeContexts)
				new LoggingInstanceServer(rdf4jServer, rdfLog)

			case None =>
				new Rdf4jInstanceServer(initRepo, readContexts, writeContexts)
		}

	}

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
	)(implicit ctxt: ExecutionContext): Future[Map[String, InstanceServer]] = {

		val instServerConfs = getAllInstanceServerConfigs(config.instanceServers)
		val valueFactory = repo.getValueFactory
		lazy val providers = providersFactory

		type ServerFutures = Map[String, Future[InstanceServer]]

		def makeNextServer(acc: ServerFutures, id: String): ServerFutures = if(acc.contains(id)) acc else {
			val servConf: InstanceServerConfig = instServerConfs(id)

			val basicInit = {
				val init = Future{makeInstanceServer(repo, servConf, config)}

				if(id == config.instanceServers.otcMetaInstanceServerId)
					init.map(new WriteNotifyingInstanceServer(_))
				else init
			}

			servConf.ingestion match{

				case Some(IngestionConfig(ingesterId, waitFor, Some(true))) =>

					val (withDependencies, dependenciesDone): (ServerFutures, Future[Unit]) = waitFor match {
						case None =>
							(acc, Future.successful(()))
						case Some(depIds) =>
							val withDeps = depIds.foldLeft(acc)(makeNextServer)
							val done = Future.sequence(depIds.map(withDeps.apply)).map(_ => ())
							(withDeps, done)
					}

					val afterIngestion = for(
						server <- basicInit;
						_ <- dependenciesDone;
						_ <- providers(ingesterId) match {
							case ingester: Ingester => Ingestion.ingest(server, ingester, valueFactory)
							case extractor: Extractor => Ingestion.ingest(server, extractor, repo)
						}
					) yield {
						log.info("all ingestion done for " + id)
						server
					}

					log.info("ingestion scheduled for " + id)
					withDependencies + (id -> afterIngestion)
				case _ =>
					acc + (id -> basicInit)
			}
		}

		val futures: ServerFutures = instServerConfs.keys.foldLeft[ServerFutures](Map.empty)(makeNextServer)
		Future.sequence(futures.map{case (id, fut) => fut.map((id, _))}).map(_.toMap)
	}

}
