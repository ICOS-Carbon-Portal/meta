package se.lu.nateko.cp.rdfstore

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.{ConfigLoader, RdfStoreConfigLoader}
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.persistence.RdfHistoryClient
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.utils.rdf4j.{accessEagerly, transact}

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import java.net.URI
import java.time.Instant

class RouteTest extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll:

	private val repo = new SailRepository(new MemoryStore)
	repo.init()

	private val sparqlServer = new Rdf4jSparqlServer(repo, SparqlServerConfig(
		maxQueryRuntimeSec = 5,
		quotaPerMinute = 60,
		quotaPerHour = 600,
		maxParallelQueries = 2,
		maxQueryQueue = 2,
		banLength = 1,
		maxCacheableQuerySize = 1024 * 1024,
		adminUsers = Nil
	))

	private given ToResponseMarshaller[SparqlQuery] = sparqlServer.marshaller
	private val historyTimestamp = Instant.parse("2026-08-04T12:00:00Z")
	private val historyUpdate = RdfUpdate(repo.getValueFactory.createStatement(
		repo.getValueFactory.createIRI("urn:history:s"),
		repo.getValueFactory.createIRI("urn:history:p"),
		repo.getValueFactory.createLiteral("history value")
	), true)
	private val route = Route(
		repo,
		message => Future.successful(s"read-only: $message"),
		_ => Seq(historyTimestamp -> historyUpdate)
	)
	private val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), 5.seconds)

	"the standalone RDF store" should:
		"configure meta to use the standalone endpoint by default" in:
			ConfigLoader.default.remoteRdfRepository.map(_.queryEndpoint.toString) shouldBe
				Some("http://127.0.0.1:9095/sparql")
			RdfStoreConfigLoader.default.rdfLogs("instances").toString shouldBe
				"http://meta.icos-cp.eu/resources/cpmeta/"

		"apply an update and expose it to a query" in:
			val update = "INSERT DATA { GRAPH <urn:test:graph> { <urn:test:s> <urn:test:p> \"value\" } }"
			Post("/update", HttpEntity(ContentTypes.`text/plain(UTF-8)`, update)) ~> route ~> check:
				status shouldBe StatusCodes.NoContent

			val query = "SELECT ?o WHERE { GRAPH <urn:test:graph> { <urn:test:s> <urn:test:p> ?o } }"
			Post("/sparql", HttpEntity(ContentTypes.`text/plain(UTF-8)`, query)) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] should include("value")

		"report health" in:
			Get("/health") ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] shouldBe "ok"

		"delegate read-only and index-dump administration" in:
			Post("/admin/read-only", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "maintenance")) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] shouldBe "read-only: maintenance"

		"serve RDF change history to meta without exposing RDF logs" in:
			val endpoint = URI.create(s"http://127.0.0.1:${binding.localAddress.getPort}/history")
			val client = RdfHistoryClient(endpoint, repo.getValueFactory)
			val graph = repo.getValueFactory.createIRI("urn:history:graph")
			Await.result(client.history(Seq(graph)), 5.seconds) shouldBe Seq(historyTimestamp -> historyUpdate)

		"serialize ASK results as standard SPARQL JSON and XML" in:
			val ask = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ASK WHERE { }")

			Post("/sparql", ask).withHeaders(Accept(Rdf4jSparqlServer.jsonSparql.mediaType)) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] should include regex "\"boolean\"\\s*:\\s*true"

			Post("/sparql", ask).withHeaders(Accept(Rdf4jSparqlServer.xmlSparql.mediaType)) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] should include regex "<boolean>\\s*true\\s*</boolean>"

		"serve RDF4J's remote Repository API, including named-graph writes" in:
			val baseUrl = s"http://127.0.0.1:${binding.localAddress.getPort}"
			val remote = new SPARQLRepository(s"$baseUrl/sparql", s"$baseUrl/update")
			remote.enableQuadMode(true)
			remote.init()
			try
				val vf = remote.getValueFactory
				val graph = vf.createIRI("urn:remote:graph")
				val subject = vf.createIRI("urn:remote:s")
				val predicate = vf.createIRI("urn:remote:p")
				val obj = vf.createLiteral("remote value")
				val statement = vf.createStatement(
					subject, predicate, obj
				)
				remote.transact(_.add(statement, graph)).isSuccess shouldBe true
				remote.accessEagerly(_.hasStatement(subject, predicate, obj, false, graph)) shouldBe true
			finally remote.shutDown()

	override protected def afterAll(): Unit =
		Await.result(binding.unbind(), 5.seconds)
		sparqlServer.shutdown()
		repo.shutDown()
		super.afterAll()
