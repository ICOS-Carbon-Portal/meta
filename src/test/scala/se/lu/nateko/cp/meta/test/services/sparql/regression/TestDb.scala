package se.lu.nateko.cp.meta.test.services.sparql.regression

import akka.Done
import akka.actor.ActorSystem
import akka.event.Logging
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.apache.commons.io.FileUtils
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery}
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Ingestion, RdfXmlFileIngester}
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationProvider, CitationStyle}
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import se.lu.nateko.cp.meta.{LmdbConfig, RdfStorageConfig}
import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}
import java.nio.file.Path
import akka.event.LoggingAdapter

private val metaConf = se.lu.nateko.cp.meta.ConfigLoader.default

class TestDb(name: String)(using system: ActorSystem) {

	private given ExecutionContext = system.dispatcher
	private given LoggingAdapter = Logging.getLogger(system, this)
	private val dir = Files.createTempDirectory(name).toAbsolutePath

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
			sail = makeSail(dir)
			_ = sail.init()
			_ = sail.initSparqlMagicIndex(Some(idxData))
		yield SailRepository(sail)

	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		repo.map(new Rdf4jSparqlRunner(_).evaluateTupleQuery(SparqlQuery(query)))

	private def ingestTriplestore(): Future[Unit] =
		val repo = SailRepository(makeSail(dir))

		val ingestion =
			given BnodeStabilizers = new BnodeStabilizers
			val factory = repo.getValueFactory
			executeSequentially(TestDb.graphIriToFile): (uriStr, filename) =>
				val graphIri = factory.createIRI(uriStr)
				val server = Rdf4jInstanceServer(repo, graphIri)
				val ingester = new RdfXmlFileIngester(s"/rdf/sparqlDbInit/$filename")
				Ingestion.ingest(server, ingester, factory).map(_ => Done)

		ingestion.map(Done => repo.shutDown())

	private def createIndex(): Future[IndexData] = {
		val sail = makeSail(dir)
		sail.init()
		for
			_ <- sail.initSparqlMagicIndex(None)
			_ <- sail.makeReadonlyDumpIndexAndCaches("Test")
			_ = sail.shutDown()
			idxData <- IndexHandler.restore()
		yield idxData
	}


	def cleanup(): Unit =
		import scala.concurrent.ExecutionContext.Implicits.global
		repo.flatMap: repo =>
			repo.shutDown()
			system.terminate()
		.onComplete: _ =>
			FileUtils.deleteDirectory(dir.toFile)
}

private def makeSail(dir: Path)(using system: ActorSystem, log: LoggingAdapter) =
	given ExecutionContext = system.dispatcher

	val rdfConf = RdfStorageConfig(
		lmdb = Some(LmdbConfig(tripleDbSize = 1L << 32, valueDbSize = 1L << 32, valueCacheSize = 1 << 13)),
		path = dir.toString,
		recreateAtStartup = false,
		indices = metaConf.rdfStorage.indices,
		disableCpIndex = false,
		recreateCpIndexAtStartup = true
	)

	val (freshInit, base) = StorageSail.apply(rdfConf, log)
	val indexUpdaterFactory = IndexHandler(system.scheduler)
	val geoFactory = GeoIndexProvider(log)
	val idxFactories = if freshInit then None
	else
		Some(indexUpdaterFactory -> geoFactory)

	val citer = new CitationProvider(base, _ => CitationClientDummy, metaConf)
	CpNotifyingSail(base, idxFactories, citer, log)

object CitationClientDummy extends CitationClient {
	override def getCitation(doi: Doi, citationStyle: CitationStyle) = Future.successful("dummy citation string")
	override def getDoiMeta(doi: Doi) = Future.successful(DoiMeta(Doi("dummy", "doi")))
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
