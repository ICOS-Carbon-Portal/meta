package se.lu.nateko.cp.meta

import java.io.Closeable

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import org.semanticweb.owlapi.apibinding.OWLManager

import akka.actor.ActorSystem
import akka.stream.Materializer
import se.lu.nateko.cp.meta.api.SparqlServer
import se.lu.nateko.cp.meta.ingestion.Extractor
import se.lu.nateko.cp.meta.ingestion.Ingester
import se.lu.nateko.cp.meta.ingestion.Ingestion
import se.lu.nateko.cp.meta.ingestion.StatementProvider
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services.FileStorageService
import se.lu.nateko.cp.meta.services.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.labeling.StationLabelingService
import se.lu.nateko.cp.meta.services.linkeddata.Rdf4jUriSerializer
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.upload.DataObjectInstanceServers
import se.lu.nateko.cp.meta.services.upload.UploadService
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner

class MetaDb private (
	val instanceServers: Map[String, InstanceServer],
	val instOntos: Map[String, InstOnto],
	val uploadService: UploadService,
	val labelingService: StationLabelingService,
	val fileService: FileStorageService,
	repo: Repository
) extends Closeable{

	val sparql: SparqlServer = new Rdf4jSparqlServer(repo)

	val uriSerializer: UriSerializer = new Rdf4jUriSerializer(repo)

	def close(): Unit = {
		for((_, server) <- instanceServers) server.shutDown()
		repo.shutDown()
	}

}

object MetaDb {

	def apply(config: CpmetaConfig)(implicit system: ActorSystem, mat: Materializer): MetaDb = {

		validateConfig(config)
		import system.dispatcher

		val ontosFut = Future{makeOntos(config.onto.ontologies)}
		val repo = Loading.empty
		val serversFut = makeInstanceServers(repo, Ingestion.allProviders, config)

		val dbFuture = for(instanceServers <- serversFut; ontos <-ontosFut) yield{
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
			new MetaDb(instanceServers, instOntos, uploadService, labelingService, fileService, repo)
		}

		Await.result(dbFuture, Duration.Inf)
	}

	def getAllInstanceServerConfigs(confs: InstanceServersConfig): Map[String, InstanceServerConfig] = {
		val dataObjServers = confs.forDataObjects

		confs.specific ++ dataObjServers.definitions.map{ servDef =>
			val writeCtxt = getInstServerContext(dataObjServers, servDef)
			(servDef.label, InstanceServerConfig(
				logName = Some(servDef.label),
				readContexts = Some(dataObjServers.commonReadContexts :+ writeCtxt),
				writeContexts = Seq(writeCtxt),
				ingestion = None
			))
		}.toMap
	}

	private def makeUploadService(
		config: CpmetaConfig,
		repo: Repository,
		instanceServers: Map[String, InstanceServer]
	)(implicit system: ActorSystem): UploadService = {
		val icosMetaInstServer = instanceServers(config.dataUploadService.icosMetaServerId)
		val factory = icosMetaInstServer.factory
		val dataObjServConfs = config.instanceServers.forDataObjects

		val allDataObjInstServConf = {
			val readContexts = dataObjServConfs.definitions.map(getInstServerContext(dataObjServConfs, _))
			InstanceServerConfig(None, Some(readContexts), Nil, None)
		}
		val allDataObjInstServ = makeInstanceServer(repo, allDataObjInstServConf, config.rdfLog)

		val perFormatServers: Map[IRI, InstanceServer] = dataObjServConfs.definitions.map{ servDef =>
			(factory.createIRI(servDef.format), instanceServers(servDef.label))
		}.toMap

		val dataObjServers = new DataObjectInstanceServers(icosMetaInstServer, allDataObjInstServ, perFormatServers)
		val sparqlRunner = new Rdf4jSparqlRunner(repo)(system.dispatcher)//rdf4j is embedded, so it will not block threads idly, but use them
		new UploadService(dataObjServers, sparqlRunner, config.dataUploadService)
	}

	private def makeInstanceServer(initRepo: Repository, conf: InstanceServerConfig, logConf: RdflogConfig): InstanceServer = {

		val factory = initRepo.getValueFactory

		val writeContexts = conf.writeContexts.map(ctxt => factory.createIRI(ctxt))
		val readContexts = conf.readContexts.getOrElse(conf.writeContexts).map(ctxt => factory.createIRI(ctxt))

		conf.logName match{
			case Some(logName) =>
				val log = PostgresRdfLog(logName, logConf, factory)
				val repo = RdfUpdateLogIngester.ingest(log.updates, initRepo, writeContexts: _*)
				val rdf4jServer = new Rdf4jInstanceServer(repo, readContexts, writeContexts)
				new LoggingInstanceServer(rdf4jServer, log)

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

		val instServerIds = config.instanceServers.specific.keys.toSet
		val schemaOntIds = config.onto.ontologies.map(_.ontoId).flatten.toSet

		def ensureInstServerExists(instanceServerId: String): Unit =
			if(!instServerIds.contains(instanceServerId))
				throw new Exception(s"Missing instance server with id '$instanceServerId'. Check your config.")

		def ensureSchemaOntExists(schemaOntId: String): Unit =
			if(!schemaOntIds.contains(schemaOntId))
				throw new Exception(s"Missing schema ontology with id '$schemaOntId'. Check your config.")
		
		ensureInstServerExists(config.dataUploadService.icosMetaServerId)

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
			val basicInit = Future{makeInstanceServer(repo, servConf, config.rdfLog)}

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
						_ <- Future{
							providers(ingesterId) match {
								case ingester: Ingester => Ingestion.ingest(server, ingester, valueFactory)
								case extractor: Extractor => Ingestion.ingest(server, extractor, repo)
							}
						}
					) yield server

					withDependencies + (id -> afterIngestion)

				case _ =>
					acc + (id -> basicInit)
			}
		}

		val futures: ServerFutures = instServerConfs.keys.foldLeft[ServerFutures](Map.empty)(makeNextServer)
		Future.sequence(futures.map{case (id, fut) => fut.map((id, _))}).map(_.toMap)
	}

	private def getInstServerContext(conf: DataObjectInstServersConfig, servDef: DataObjectInstServerDefinition) =
		new java.net.URI(conf.uriPrefix.toString + servDef.label + "/")

}
