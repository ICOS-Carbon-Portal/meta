package se.lu.nateko.cp.meta.test.services.sparql.regression

import akka.Done
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.apache.commons.io.FileUtils
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Outcome
import org.scalatest.Suite
import org.scalatest.fixture
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.meta.RdfStorageConfig
import se.lu.nateko.cp.meta.RdfStorageConfig.apply
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.ingestion.Ingestion
import se.lu.nateko.cp.meta.ingestion.RdfXmlFileIngester
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.citation.CitationClient
import se.lu.nateko.cp.meta.services.citation.CitationClient.CitationCache
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.citation.CitationStyle
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import se.lu.nateko.cp.meta.services.sparql.magic.CpNativeStore
import se.lu.nateko.cp.meta.services.sparql.magic.CpNotifyingSail
import se.lu.nateko.cp.meta.services.sparql.magic.GeoIndexProvider
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler
import se.lu.nateko.cp.meta.test.services.sparql.SparqlRouteTests
import se.lu.nateko.cp.meta.test.services.sparql.SparqlTests
import se.lu.nateko.cp.meta.utils.async.executeSequentially

import java.nio.file.Files
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import se.lu.nateko.cp.meta.utils.asOptInstanceOf

class TestDb {

	private val metaConf = se.lu.nateko.cp.meta.ConfigLoader.withDummyPasswords

	val akkaConf = ConfigFactory.defaultReference()
		.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("INFO"))
	private given system: ActorSystem = ActorSystem("sparqlRegrTesting", akkaConf)
	import system.{dispatcher, log}

	val dir = Files.createTempDirectory("sparqlRegrTesting").toAbsolutePath

	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		repo.map(new Rdf4jSparqlRunner(_).evaluateTupleQuery(SparqlQuery(query)))

	val repo: Future[Repository] = {
		object CitationClientDummy extends CitationClient{
			override def getCitation(doi: Doi, citationStyle: CitationStyle) = Future.successful("dummy citation string")
			override def getDoiMeta(doi: Doi) = Future.successful(DoiMeta(Doi("dummy", "doi")))
		}

		val rdfConf = RdfStorageConfig(
			path = dir.toString,
			recreateAtStartup = false,
			indices = metaConf.rdfStorage.indices,
			disableCpIndex = false,
			recreateCpIndexAtStartup = true
		)

		val indexUpdaterFactory = IndexHandler(system.scheduler)
		val geoFactory = GeoIndexProvider(log)

		def makeSail =
			val (freshInit, base) = CpNativeStore.apply(rdfConf, log)
			val idxFactories = if freshInit then None else
				Some(indexUpdaterFactory -> geoFactory)

			val citer = new CitationProvider(base, dois => CitationClientDummy, metaConf)
			CpNotifyingSail(base, idxFactories, citer, log)

		/**
		The repo is created three times:
			0) to ingest the test RDF file into a fresh new triplestore
			1) to restart the triplestore to create the magic SPARQL index
			2) to dump the SPARQL index to disk, re-start, read the index
			data structure, and initialize the index from it
		**/
		val repo0Fut: Future[Repository] =
			val repo0 = SailRepository(makeSail)
			val factory = repo0.getValueFactory

			executeSequentially(TestDb.graphIriToFile): (uriStr, filename) =>
				val graphIri = factory.createIRI(uriStr)
				val server = Rdf4jInstanceServer(repo0, graphIri)
				val ingester = new RdfXmlFileIngester(s"/rdf/sparqlDbInit/$filename")
				Ingestion.ingest(server, ingester, factory).map(_ => Done)
			.map: _ =>
				repo0
		for
			repo0 <- repo0Fut
			repo1 = {
				repo0.shutDown()
				val repo1 = makeSail.asInstanceOf[CpNotifyingSail]
				repo1.init()
				repo1.initSparqlMagicIndex(None)
				repo1
			}
			_ <- repo1.makeReadonlyDumpIndexAndCaches("Test")
			_ = repo1.shutDown()
			idxData <- IndexHandler.restore()
			repo2 = makeSail.asInstanceOf[CpNotifyingSail]
			_ <- {
				repo2.init()
				repo2.initSparqlMagicIndex(Some(idxData))
			}
		yield
			SailRepository(repo2)

	}

	def cleanup(): Unit = {
		repo.onComplete{repoTry =>
			repoTry.foreach(_.shutDown())
			FileUtils.deleteDirectory(dir.toFile)
		}(scala.concurrent.ExecutionContext.Implicits.global)
		system.terminate()
	}
}

object TestDb:
	val graphIriToFile = Seq(
			"atmprodcsv", "cpmeta", "ecocsv", "etcbin", "etcprodcsv", "excel",
			"extrastations", "icos", "netcdf", "stationentry", "stationlabeling"
		).map{id =>
			s"http://meta.icos-cp.eu/resources/$id/" -> s"$id.rdf"
		}.toMap +
		("https://meta.fieldsites.se/resources/sites/" -> "sites.rdf") +
		("http://meta.icos-cp.eu/ontologies/cpmeta/" -> "cpmeta.owl") +
		("http://meta.icos-cp.eu/ontologies/stationentry/" -> "stationEntry.owl") +
		("http://meta.icos-cp.eu/collections/" -> "collections.rdf") +
		("http://meta.icos-cp.eu/documents/" -> "icosdocs.rdf")

	lazy val instance = new TestDb
