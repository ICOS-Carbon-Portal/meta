package se.lu.nateko.cp.meta.services.sparql.regression

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import org.apache.commons.io.FileUtils
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationProvider, CitationStyle}
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataService
import se.lu.nateko.cp.meta.services.sparql.magic.{CpNotifyingSail, GeoIndexProvider, IndexHandler, StorageSail}
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import se.lu.nateko.cp.meta.{LmdbConfig, RdfStorageConfig, RdfStoreConfigLoader}

import java.nio.file.{Files, Path}
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

	private lazy val dir = Files.createTempDirectory("testdb").toAbsolutePath
	private given system: ActorSystem = ActorSystem("TestDb")
	private given ExecutionContext = system.dispatcher
	private given log: LoggingAdapter = Logging.getLogger(system, this)

	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		Future.apply(new Rdf4jSparqlRunner(repo).evaluateTupleQuery(query))

	private def initRepo(): Future[Repository] = {

		/**
		The repo is created twice:
			1) to ingest the test RDF file into a fresh new triplestore
			2) to restart the triplestore and create the magic SPARQL index
		**/

		log.info("Initializing")
		val start = System.currentTimeMillis()
		for
			() <- ingestTriplestore(dir)
			sail = makeSail(dir)
			() = sail.init()
			_ <- sail.initSparqlMagicIndex()
			() = log.info(s"TestDb init: ${System.currentTimeMillis() - start} ms")
		yield SailRepository(sail)
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
			FileUtils.deleteDirectory(dir.toFile)
		}
	}
}

// Regression fixtures are loaded directly with RDF4J, instead of going through meta's
// Ingestion/RdfXmlFileIngester/BnodeStabilizers pipeline (see task 17 in
// docs/rdf-common-split): that pipeline lives in `meta`, and using it here would recreate a
// meta -> rdfStore -> meta dependency cycle. Blank-node stabilization is not needed either:
// none of the regression queries key off stable "bnode_N" identifiers, they only rely on the
// RDF structure loaded from the fixtures.
private def ingestTriplestore(dir: Path)(using ActorSystem, ExecutionContext): Future[Unit] = Future {
	val repo = SailRepository(makeSail(dir))
	repo.init()
	graphIriToFile.foreach { (uriStr, filename) =>
		Loading.loadResource(repo, s"/rdf/sparqlDbInit/$filename", uriStr, RDFFormat.RDFXML).get
	}
	repo.shutDown()
}

private def makeSail(dir: Path)(using ExecutionContext)(using system: ActorSystem) = {
	val rdfConf = RdfStorageConfig(
		lmdb = Some(LmdbConfig(tripleDbSize = 1L << 32, valueDbSize = 1L << 32, valueCacheSize = 1 << 13)),
		path = dir.toString,
		indices = "spoc,posc,opsc",
		disableCpIndex = false
	)

	val base = StorageSail.apply(rdfConf)
	val indexUpdaterFactory = IndexHandler(system.scheduler)
	val geoFactory = GeoIndexProvider()
	val idxFactories = Some(indexUpdaterFactory -> geoFactory)

	val citer = {
			val config = RdfStoreConfigLoader.citationStoreConfig
			new CitationProvider(
			base, _ => CitationClientDummy, config.core,
			CitationProvider.getLenses(config.instanceServers, config.dataUploadService),
			CitationProvider.pidFactory(config)
		)
	}

	import TestRepo.given
	CpNotifyingSail(base, idxFactories, citer, DerivedMetadataService(citer))
}

object CitationClientDummy extends CitationClient {
	override def getCitation(doi: Doi, citationStyle: CitationStyle) = Future.successful("dummy citation string")
	override def getDoiMeta(doi: Doi) = Future.successful(DoiMeta(Doi("dummy", "doi")))
}
