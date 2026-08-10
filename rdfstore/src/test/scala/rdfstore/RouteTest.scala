package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Origin`, Accept, HttpOrigin, Origin, RawHeader}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.{ConfigLoader, RdfStoreConfigLoader}
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataService
import se.lu.nateko.cp.meta.utils.rdf4j.{accessEagerly, transact}

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class RouteTest extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll:

	private val repo = new SailRepository(new MemoryStore)
	repo.init()

	private val sparqlConf = SparqlServerConfig(
		maxQueryRuntimeSec = 5,
		quotaPerMinute = 60,
		quotaPerHour = 600,
		maxParallelQueries = 2,
		maxQueryQueue = 2,
		banLength = 1,
		maxCacheableQuerySize = 1024 * 1024,
		adminUsers = Nil
	)
	private val sparqlServer = new Rdf4jSparqlServer(repo, sparqlConf)
	private val forwardedFor = RawHeader("X-Forwarded-For", "192.0.2.1")

	private given ToResponseMarshaller[SparqlRequest] = sparqlServer.marshaller
	private val route = Route(
		repo,
		sparqlConf,
		DerivedMetadataService.unavailable(repo.getValueFactory),
		message => Future.successful(s"read-only: $message")
	)
	private val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), 5.seconds)

	"the standalone RDF store" should:
		"configure meta to use the standalone endpoint by default" in:
			ConfigLoader.default.remoteRdfRepository.map(_.queryEndpoint.toString) shouldBe
				Some("http://127.0.0.1:9095/internal/sparql")
			ConfigLoader.default.remoteRdfRepository.map(_.updateEndpoint.toString) shouldBe
				Some("http://127.0.0.1:9095/internal/sparql")
			ConfigLoader.default.instanceServers.specific("instances").logName shouldBe Some("instances")
			ConfigLoader.default.rdfLog shouldBe RdfStoreConfigLoader.default.rdfLog
			ConfigLoader.default.rdfStorage shouldBe RdfStoreConfigLoader.default.rdfStorage
			RdfStoreConfigLoader.default.rdfLogs("instances").toString shouldBe
				"http://meta.icos-cp.eu/resources/cpmeta/"

		"report health" in:
			Get("/health") ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] shouldBe "ok"

		"delegate read-only and index-dump administration" in:
			Post("/admin/read-only", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "maintenance")) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] shouldBe "read-only: maintenance"

		"serve a versioned derived-metadata batch response" in:
			Post(
				"/internal/derived/v1/resolve",
				HttpEntity(ContentTypes.`application/json`, """{"resources":["urn:test:missing"]}""")
			) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] should include("\"version\":1")
				responseAs[String] should include("\"status\":\"unavailable\"")

		"reject public SPARQL requests that did not pass through the trusted proxy" in:
			Post("/sparql", "ASK WHERE { }") ~> route ~> check:
				status shouldBe StatusCodes.BadRequest
				responseAs[String] should include("trusted reverse proxy")

		"cache and CORS-enable the public SPARQL endpoint" in:
			val query = "SELECT ?o WHERE { GRAPH <urn:cache:graph> { ?s ?p ?o } }"
			def request = Post("/sparql", HttpEntity(ContentTypes.`text/plain(UTF-8)`, query))
				.withHeaders(Origin(HttpOrigin("https://example.icos-cp.eu")), forwardedFor)

			request ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String]
				header(`Access-Control-Allow-Origin`.name).map(_.value) shouldBe
					Some("https://example.icos-cp.eu")
				header(SparqlRoute.X_Cache_Status).map(_.value) shouldBe Some("MISS")

			Thread.sleep(100) // the cache is populated asynchronously, as the response streams out

			request ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String]
				header(SparqlRoute.X_Cache_Status).map(_.value) shouldBe Some("HIT")

		"serialize ASK results as standard SPARQL JSON and XML" in:
			val ask = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ASK WHERE { }")

			Post("/sparql", ask).withHeaders(Accept(Rdf4jSparqlServer.jsonSparql.mediaType), forwardedFor) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] should include regex "\"boolean\"\\s*:\\s*true"

			Post("/sparql", ask).withHeaders(Accept(Rdf4jSparqlServer.xmlSparql.mediaType), forwardedFor) ~> route ~> check:
				status shouldBe StatusCodes.OK
				responseAs[String] should include regex "<boolean>\\s*true\\s*</boolean>"

		"serve RDF4J's remote Repository API, including named-graph writes" in:
			val baseUrl = s"http://127.0.0.1:${binding.localAddress.getPort}"
			val remote = new SPARQLRepository(s"$baseUrl/internal/sparql", s"$baseUrl/internal/sparql")
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
