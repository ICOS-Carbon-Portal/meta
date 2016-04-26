package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import java.io.Closeable
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import org.openrdf.model.Statement
import org.openrdf.model.ValueFactory
import org.openrdf.repository.Repository
import org.semanticweb.owlapi.apibinding.OWLManager
import se.lu.nateko.cp.meta.ingestion.Ingestion
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.services._
import se.lu.nateko.cp.meta.services.labeling.StationLabelingService
import se.lu.nateko.cp.meta.services.upload.UploadService
import se.lu.nateko.cp.meta.utils.sesame._

class MetaDb private (
	val instanceServers: Map[String, InstanceServer],
	val instOntos: Map[String, InstOnto],
	val uploadService: UploadService,
	val labelingService: StationLabelingService,
	val fileService: FileStorageService,
	repo: Repository) extends Closeable{

	val sparql: SparqlServer = new SesameSparqlServer(repo)

	def close(): Unit = {
		for((_, server) <- instanceServers) server.shutDown()
		repo.shutDown()
	}

}

object MetaDb {

	def makeInstanceServer(initRepo: Repository, conf: InstanceServerConfig, logConf: RdflogConfig): InstanceServer = {

		val factory = initRepo.getValueFactory

		val writeContexts = conf.writeContexts.map(ctxt => factory.createURI(ctxt))
		val readContexts = conf.readContexts.getOrElse(conf.writeContexts).map(ctxt => factory.createURI(ctxt))

		conf.logName match{
			case Some(logName) =>
				val log = PostgresRdfLog(logName, logConf, factory)
				val repo = RdfUpdateLogIngester.ingest(log.updates, initRepo, writeContexts: _*)
				val sesameServer = new SesameInstanceServer(repo, readContexts, writeContexts)
				new LoggingInstanceServer(sesameServer, log)

			case None =>
				new SesameInstanceServer(initRepo, readContexts, writeContexts)
		}

	}

	def makeOntos(confs: Seq[SchemaOntologyConfig]): Map[String, Onto] = {
		val owlManager = OWLManager.createOWLOntologyManager

		confs.foldLeft(Map.empty[String, Onto])((acc, conf) => {
			val owl = utils.owlapi.getOntologyFromJarResourceFile(conf.owlResource, owlManager)

			conf.ontoId match{
				case None => acc
				case Some(ontId) => acc + ((ontId, new Onto(owl)))
			}
		})
	}

	def validateConfig(config: CpmetaConfig): Unit = {

		val instServerIds = config.instanceServers.keys.toSet
		val schemaOntIds = config.onto.ontologies.map(_.ontoId).flatten.toSet

		def ensureInstServerExists(instanceServerId: String): Unit =
			if(!instServerIds.contains(instanceServerId))
				throw new Exception(s"Missing instance server with id '$instanceServerId'. Check your config.")

		def ensureSchemaOntExists(schemaOntId: String): Unit =
			if(!schemaOntIds.contains(schemaOntId))
				throw new Exception(s"Missing schema ontology with id '$schemaOntId'. Check your config.")
		
		ensureInstServerExists(config.dataUploadService.instanceServerId)

		config.onto.instOntoServers.values.foreach{ conf =>
			ensureInstServerExists(conf.instanceServerId)
			ensureSchemaOntExists(conf.ontoId)
		}
	}

	def apply(config: CpmetaConfig)(implicit system: ActorSystem): MetaDb = {

		validateConfig(config)
		import system.dispatcher

		val ontosFut = Future{makeOntos(config.onto.ontologies)}

		val repo = Loading.empty
		val valueFactory = repo.getValueFactory

		val fileService = new FileStorageService(new java.io.File(config.fileStoragePath))
		val ingesters = Ingestion.allIngesters

		def performIngestion(ingesterId: String, serverFut: Future[InstanceServer]): Future[InstanceServer] = {

			val statementsFut: Future[Iterator[Statement]] = Future{
				ingesters(ingesterId).getStatements(valueFactory)
			}
	
			for(
				server <- serverFut;
				statements <- statementsFut;
				afterIngestion <- Future{
					Ingestion.ingest(server, statements)
					server
				}
			) yield afterIngestion
		}

		
		val instanceServersFut: Future[Map[String, InstanceServer]] = {

			val tupleFuts: Iterable[Future[(String, InstanceServer)]] = config.instanceServers.map{
				case (servId, servConf) =>
					val serverFut = Future{makeInstanceServer(repo, servConf, config.rdfLog)}

					val finalFut = servConf.ingestion match{
						case Some(IngestionConfig(ingesterId, Some(true), _)) => performIngestion(ingesterId, serverFut)
						case _ => serverFut
					}
					finalFut.map((servId, _))
			}
			Future.sequence(tupleFuts).map(_.toMap)
		}

		val dbFuture = for(
			ontos <- ontosFut;
			instanceServers <- instanceServersFut
		) yield{
			val instOntos = config.onto.instOntoServers.map{
				case (servId, servConf) =>
					val instServer = instanceServers(servConf.instanceServerId)
					val onto = ontos(servConf.ontoId)
					(servId, new InstOnto(instServer, onto))
			}

			val uploadInstServer = instanceServers(config.dataUploadService.instanceServerId)
			val uploadService = new UploadService(uploadInstServer, config.dataUploadService)

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
}