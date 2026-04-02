package se.lu.nateko.cp.meta.test.services.sparql.regression

import scala.language.unsafeNulls

import akka.Done
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Ingestion, RdfXmlFileIngester}
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

private val graphIriToFile = Seq(
	"atmprodcsv",
	"cpmeta",
	"ecocsv",
	"etcbin",
	"etcprodcsv",
	"excel",
	"extrastations",
	"icos",
	"netcdf",
	"stationentry",
	"stationlabeling"
).map { id =>
	s"http://meta.icos-cp.eu/resources/$id/" -> s"$id.rdf"
}.toMap +
	("https://meta.fieldsites.se/resources/sites/" -> "sites.rdf") +
	("http://meta.icos-cp.eu/ontologies/cpmeta/" -> "cpmeta.owl") +
	("http://meta.icos-cp.eu/ontologies/stationentry/" -> "stationEntry.owl") +
	("http://meta.icos-cp.eu/collections/" -> "collections.rdf") +
	("http://meta.icos-cp.eu/documents/" -> "icosdocs.rdf")

class TestDb {
	TestRepo.checkout()

	val repo: Repository = TestRepo.repo
	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		TestRepo.runSparql(query)

	override def finalize(): Unit = {
		TestRepo.close()
	}
}

private object TestRepo {
	given EnvriConfigs = MetaCoreConfig.default.envriConfigs

	lazy val repo = Await.result(initRepo(), Duration.Inf)
	private var reference_count = 0
	private var open = false

	private given system: ActorSystem = ActorSystem("TestDb")
	private given ExecutionContext = system.dispatcher
	private given log: LoggingAdapter = Logging.getLogger(system, this)

	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		Future.apply(new Rdf4jSparqlRunner(repo).evaluateTupleQuery(SparqlQuery(query)))

	private def initRepo(): Future[Repository] = {
		log.info("Initializing")
		val start = System.currentTimeMillis()
		val repo = Loading.emptyInMemory
		for
			() <- ingestTriplestore(repo)
			() = log.info(s"TestDb init: ${System.currentTimeMillis() - start} ms")
		yield repo
	}

	def checkout() = {
		log.info("Checkout")
		open = true
		reference_count += 1;
	}

	def close() = {
		reference_count -= 1;
		if (open && reference_count <= 0) {
			log.info("Cleaning up!")
			open = false
			repo.shutDown()
		}
	}
}

private def ingestTriplestore(repo: Repository)(using ActorSystem, ExecutionContext): Future[Unit] = {
	val ingestion =
		given BnodeStabilizers = new BnodeStabilizers
		val factory = repo.getValueFactory
		executeSequentially(graphIriToFile): (uriStr, filename) =>
			val graphIri = factory.createIRI(uriStr)
			val server = Rdf4jInstanceServer(repo, graphIri)
			val ingester = new RdfXmlFileIngester(s"/rdf/sparqlDbInit/$filename")
			Ingestion.ingest(server, ingester, factory).map(_ => Done)

	ingestion.map(_ => ())
}
