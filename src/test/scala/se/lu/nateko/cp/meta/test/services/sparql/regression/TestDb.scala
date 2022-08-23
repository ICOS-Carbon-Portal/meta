package se.lu.nateko.cp.meta.test.services.sparql.regression

import akka.Done
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.apache.commons.io.FileUtils
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import se.lu.nateko.cp.doi.Doi
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
import se.lu.nateko.cp.meta.services.citation.CitationProviderFactory
import se.lu.nateko.cp.meta.services.citation.CitationStyle
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import se.lu.nateko.cp.meta.services.sparql.magic.CpNativeStore
import se.lu.nateko.cp.meta.services.sparql.magic.IndexHandler
import se.lu.nateko.cp.meta.utils.async.executeSequentially

import java.nio.file.Files
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class TestDb {

	private val metaConf = se.lu.nateko.cp.meta.ConfigLoader.default
	val akkaConf = ConfigFactory.defaultReference()
		.withValue("akka.loglevel", ConfigValueFactory.fromAnyRef("ERROR"))
	private val system = ActorSystem("sparqlRegrTesting", akkaConf)
	import system.{dispatcher, log}

	val dir = Files.createTempDirectory("sparqlRegrTesting").toAbsolutePath

	def runSparql(query: String): Future[CloseableIterator[BindingSet]] =
		repo.map(new Rdf4jSparqlRunner(_).evaluateTupleQuery(SparqlQuery(query)))

	val repo: Future[Repository] = {
		object CitationClientDummy extends CitationClient{
			override def getCitation(doi: Doi, citationStyle: CitationStyle) = Future.successful("dummy citation string")
		}
		val citerFactory: CitationProviderFactory =
			sail => CitationProvider(CitationClientDummy, sail, metaConf.core, metaConf.dataUploadService)

		val rdfConf = RdfStorageConfig(
			path = dir.toString,
			recreateAtStartup = false,
			indices = metaConf.rdfStorage.indices,
			disableCpIndex = false,
			recreateCpIndexAtStartup = true
		)

		val indexUpdaterFactory = (idx: CpIndex) => new IndexHandler(idx, system.scheduler, log)

		def makeSail = new CpNativeStore(rdfConf, indexUpdaterFactory, citerFactory, log)

		val repo0 = new SailRepository(makeSail)
		val factory = repo0.getValueFactory

		val fut = executeSequentially(TestDb.graphIriToFile){(uriStr, filename) =>
			val graphIri = factory.createIRI(uriStr)
			val server = Rdf4jInstanceServer(repo0, graphIri)
			val ingester = new RdfXmlFileIngester(s"/rdf/sparqlDbInit/$filename")
			Ingestion.ingest(server, ingester, factory).map(_ => Done)
		}
		fut.map{_ =>
			repo0.shutDown()
			val sail = makeSail
			val repo1 = new SailRepository(sail)
			repo1.init()
			sail.initSparqlMagicIndex(None)
			repo1
		}
	}

	def cleanup(): Unit = {
		repo.onComplete{repoTry =>
			repoTry.foreach(_.shutDown())
			FileUtils.deleteDirectory(dir.toFile)
		}(scala.concurrent.ExecutionContext.Implicits.global)
		system.terminate()
	}
}

object TestDb{
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
}