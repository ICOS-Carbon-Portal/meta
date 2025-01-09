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
import se.lu.nateko.cp.meta.LmdbConfig
import se.lu.nateko.cp.meta.RdfStorageConfig
import se.lu.nateko.cp.meta.RdfStorageConfig.apply
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.ingestion.BnodeStabilizers
import se.lu.nateko.cp.meta.ingestion.Ingestion
import se.lu.nateko.cp.meta.ingestion.RdfXmlFileIngester
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.citation.CitationClient
import se.lu.nateko.cp.meta.services.citation.CitationClient.CitationCache
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.citation.CitationStyle
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import se.lu.nateko.cp.meta.services.sparql.magic.CpNotifyingSail
import se.lu.nateko.cp.meta.services.sparql.magic.GeoIndexProvider
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler
import se.lu.nateko.cp.meta.services.sparql.magic.StorageSail
import se.lu.nateko.cp.meta.test.services.sparql.SparqlTests
import se.lu.nateko.cp.meta.utils.asOptInstanceOf
import se.lu.nateko.cp.meta.utils.async.executeSequentially

import java.nio.file.Files
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData

class TestDb(name: String) {
	import system.{dispatcher, log}

	private given system: ActorSystem = ActorSystem("sparqlRegrTesting", akkaConf)
	private val dir = Files.createTempDirectory(name).toAbsolutePath
	private val metaConf = se.lu.nateko.cp.meta.ConfigLoader.default
	private val akkaConf =
		ConfigFactory.defaultReference().withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("INFO"))

	val repo: Future[Repository] =
		/**
		The repo is created three times:
			1) to ingest the test RDF file into a fresh new triplestore
			2) to restart the triplestore to create the magic SPARQL index
			3) to dump the SPARQL index to disk, re-start, read the index
			data structure, and initialize the index from it
		**/
		for
			_ <- ingestTriplestore()
			idxData <- createIndex()
			sail = makeSail()
			_ = sail.init()
			_ = sail.initSparqlMagicIndex(Some(idxData))
		yield SailRepository(sail)

	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		repo.map(new Rdf4jSparqlRunner(_).evaluateTupleQuery(SparqlQuery(query)))

	private def ingestTriplestore(): Future[Unit] =
		val repo = SailRepository(makeSail())

		val ingestion =
			given BnodeStabilizers = new BnodeStabilizers
			val factory = repo.getValueFactory
			executeSequentially(TestDb.graphIriToFile): (uriStr, filename) =>
				val graphIri = factory.createIRI(uriStr)
				val server = Rdf4jInstanceServer(repo, graphIri)
				val ingester = new RdfXmlFileIngester(s"/rdf/sparqlDbInit/$filename")
				Ingestion.ingest(server, ingester, factory).map(_ => Done)

		ingestion.map(_ -> repo.shutDown())

	private def createIndex(): Future[IndexData] = {
		val sail = makeSail()
		sail.init()
		for
			_ <- sail.initSparqlMagicIndex(None)
			_ <- sail.makeReadonlyDumpIndexAndCaches("Test")
			_ = sail.shutDown()
			idxData <- IndexHandler.restore()
		yield idxData
	}

	private def makeSail() =
		val rdfConf = RdfStorageConfig(
			lmdb = Some(LmdbConfig(tripleDbSize = 1L << 32, valueDbSize = 1L << 32, valueCacheSize = 1 << 13)),
			path = dir.toString,
			recreateAtStartup = false,
			indices = metaConf.rdfStorage.indices,
			disableCpIndex = false,
			recreateCpIndexAtStartup = true
		)

		object CitationClientDummy extends CitationClient {
			override def getCitation(doi: Doi, citationStyle: CitationStyle) = Future.successful("dummy citation string")
			override def getDoiMeta(doi: Doi) = Future.successful(DoiMeta(Doi("dummy", "doi")))
		}

		val (freshInit, base) = StorageSail.apply(rdfConf, log)
		val indexUpdaterFactory = IndexHandler(system.scheduler)
		val geoFactory = GeoIndexProvider(log)
		val idxFactories = if freshInit then None
		else
			Some(indexUpdaterFactory -> geoFactory)

		val citer = new CitationProvider(base, dois => CitationClientDummy, metaConf)
		CpNotifyingSail(base, idxFactories, citer, log)

	def cleanup(): Unit =
		import scala.concurrent.ExecutionContext.Implicits.global
		repo.flatMap: repo =>
			repo.shutDown()
			system.terminate()
		.onComplete: _ =>
			FileUtils.deleteDirectory(dir.toFile)
}

object TestDb:
	val graphIriToFile = Seq(
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
