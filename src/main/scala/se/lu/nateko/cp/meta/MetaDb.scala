package se.lu.nateko.cp.meta

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import org.openrdf.repository.Repository
import org.semanticweb.owlapi.apibinding.OWLManager
import scala.concurrent.Future
import se.lu.nateko.cp.meta.ingestion.Ingestion
import org.openrdf.model.Statement
import org.openrdf.model.ValueFactory
import scala.concurrent.ExecutionContext
import java.io.Closeable
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import se.lu.nateko.cp.meta.sparqlserver.SparqlServer
import se.lu.nateko.cp.meta.sparqlserver.SesameSparqlServer

class MetaDb private (
	val instanceServers: Map[String, InstanceServer],
	val onto: Onto,
	val instOnto: InstOnto,
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
		val readContexts = conf.readContexts.getOrElse(Nil).map(ctxt => factory.createURI(ctxt))

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

	def makeOnto(conf: OntoConfig): Onto = {
		val manager = OWLManager.createOWLOntologyManager
		val owl = utils.owlapi.getOntologyFromJarResourceFile(conf.owlResource, manager)
		new Onto(owl)
	}

	def performIngestion(ingesterId: String, serverFut: Future[InstanceServer], factory: ValueFactory)
												(implicit ex: ExecutionContext): Future[InstanceServer] = {
		val statementsFut: Future[Iterator[Statement]] = Future{
			Ingestion.allIngesters(ingesterId).getStatements(factory)
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

	def apply(config: CpmetaConfig)(implicit ex: ExecutionContext): MetaDb = {
		val ontoFut = Future{makeOnto(config.onto)}

		val repo = Loading.empty
		val valueFactory = repo.getValueFactory

		val serverKeys = config.instanceServers.keys.toIndexedSeq

		val serverFuts = config.instanceServers.values.map { servConf =>
			val serverFut = Future{makeInstanceServer(repo, servConf, config.rdfLog)}
			servConf.ingestion match{
				case Some(IngestionConfig(ingesterId, Some(true), _)) => performIngestion(ingesterId, serverFut, valueFactory)
				case _ => serverFut
			}
		}.toIndexedSeq

		val dbFuture = for(
			onto <- ontoFut;
			instServer <- serverFuts(serverKeys.indexOf(config.onto.instanceServerId));
			servers <- Future.sequence(serverFuts)
		) yield{
			val instOnto = new InstOnto(instServer, onto)
			val instServers = serverKeys.zip(servers).toMap
			new MetaDb(instServers, onto, instOnto, repo)
		}

		Await.result(dbFuture, Duration.Inf)
	}
}