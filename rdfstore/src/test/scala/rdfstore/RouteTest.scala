package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.api.SparqlQuery

import se.lu.nateko.cp.meta.routes.SparqlRoute

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpHeader, HttpMethods, HttpRequest, StatusCodes}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Origin`, Accept, HttpOrigin, Origin, RawHeader}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.eclipse.rdf4j.common.iteration.EmptyIteration
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import se.lu.nateko.cp.meta.{ConfigLoader, RemoteSyncConfig, SparqlServerConfig}
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, Licence, References}
import se.lu.nateko.cp.meta.persistence.RdfLogManager
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.derived.{DerivedMetadata, DerivedMetadataJsonProtocol, DerivedMetadataRequest, DerivedMetadataResponse, DerivedMetadataService}
import se.lu.nateko.cp.meta.services.sparql.Rdf4jSparqlServer
import se.lu.nateko.cp.meta.services.sparql.magic.StatementsEnricher
import se.lu.nateko.cp.meta.services.sync.RemoteSyncWorker
import se.lu.nateko.cp.meta.utils.rdf4j.{accessEagerly, transact}

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import java.net.URI
import spray.json.*

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

	private given ToResponseMarshaller[SparqlQuery] = sparqlServer.marshaller
	private given EnvriConfigs = ConfigLoader.default.core.envriConfigs
	private val route = Route(
		repo,
		sparqlConf,
		DerivedMetadataService.unavailable(repo.getValueFactory),
		message => Future.successful(s"read-only: $message")
	)
	private val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), 5.seconds)

	"the standalone RDF store" should:
		"find the RDF logs to restore in the shared cpmeta config" in:
			val config = ConfigLoader.default
			val logs = RdfLogManager.configuredLogs(config.instanceServers).map(c => c.name -> c).toMap
			logs("instances").context.toString shouldBe
				"http://meta.icos-cp.eu/resources/cpmeta/"
			logs("wdcgg").context.toString shouldBe
				"http://meta.icos-cp.eu/resources/wdcgg/"

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

		"keep derived route values in parity with SPARQL magic triples" in:
			import DerivedMetadataJsonProtocol.given
			val vf = repo.getValueFactory
			val resource = new URI("urn:test:derived:parity")
			val licence = Licence(new URI("https://example.org/licence"), "Example licence", new URI("https://example.org/licence-info"), None)
			val references = References.empty.copy(title = Some("Parity item"), citationString = Some("Citation from references"), licence = Some(licence))
			val metadata = DerivedMetadata(resource, references, Some("Canonical citation"), Some(licence))
			val derived = DerivedMetadataService.fixed(vf, scala.collection.immutable.Map(resource -> metadata))
			val parityRoute = Route(repo, sparqlConf, derived, _ => Future.successful("unused"))

			Post(
				"/internal/derived/v1/resolve",
				HttpEntity(ContentTypes.`application/json`, DerivedMetadataRequest(Seq(resource)).toJson.compactPrint)
			) ~> parityRoute ~> check:
				val result = responseAs[String].parseJson.convertTo[DerivedMetadataResponse].results.head
				result.metadata shouldBe Some(metadata)

			val vocab = new CpmetaVocab(vf)
			val iteration = StatementsEnricher(derived, vocab).enrich(new EmptyIteration, vf.createIRI(resource.toString), null, null)
			val values = scala.collection.mutable.Map.empty[org.eclipse.rdf4j.model.IRI, String]
			try while iteration.hasNext do
				val statement = iteration.next()
				values(statement.getPredicate) = statement.getObject.stringValue
			finally iteration.close()

			values(vocab.hasCitationString) shouldBe "Canonical citation"
			values(vocab.dcterms.license) shouldBe licence.url.toString
			values(vocab.hasBiblioInfo).parseJson.asJsObject.fields("title") shouldBe JsString("Parity item")

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
				val otherGraph = vf.createIRI("urn:remote:other-graph")
				val otherSubject = vf.createIRI("urn:remote:other-s")
				val statement = vf.createStatement(
					subject, predicate, obj
				)
				val otherStatement = vf.createStatement(otherSubject, predicate, obj)
				remote.transact: conn =>
					conn.add(statement, graph)
					conn.add(otherStatement, otherGraph)
				.isSuccess shouldBe true
				remote.accessEagerly(_.hasStatement(subject, predicate, obj, false, graph)) shouldBe true
				remote.accessEagerly(_.hasStatement(otherSubject, predicate, obj, false, graph)) shouldBe false
				remote.accessEagerly: conn =>
					val statements = conn.getStatements(null, null, null, false, graph)
					try statements.iterator().asScala.toSeq should contain only statement
					finally statements.close()
			finally remote.shutDown()

		"serve as the remote endpoint for the synchronization worker of another RDF store" in:
			val baseUrl = s"http://127.0.0.1:${binding.localAddress.getPort}"
			val vf = repo.getValueFactory
			val metaVocab = CpmetaVocab(vf)
			val graph = vf.createIRI("urn:sync:graph")
			val person = vf.createIRI("urn:sync:person")
			val name = vf.createLiteral("Synced")
			repo.transact: conn =>
				conn.add(person, RDF.TYPE, metaVocab.personClass, graph)
				conn.add(person, metaVocab.hasFirstName, name, graph)
			.isSuccess shouldBe true

			val local = new SailRepository(new MemoryStore)
			local.init()
			local.transact(_.add(person, RDF.TYPE, metaVocab.personClass, graph)).isSuccess shouldBe true
			val syncConf = RemoteSyncConfig(
				enabled = true,
				endpoint = URI(s"$baseUrl/internal/sparql"),
				kinds = Some(Seq("people")),
				prune = false,
				pauseBetweenRunsMinutes = 60
			)
			an[IllegalArgumentException] should be thrownBy
				RemoteSyncWorker.start(local, syncConf.copy(kinds = Some(Seq("planets"))))
			RemoteSyncWorker.start(local, syncConf.copy(enabled = false)) shouldBe None

			val worker = RemoteSyncWorker.start(local, syncConf)
			try
				def synced = local.accessEagerly(_.hasStatement(person, metaVocab.hasFirstName, name, false, graph))
				val deadline = 10.seconds.fromNow
				while !synced && deadline.hasTimeLeft() do Thread.sleep(50)
				synced shouldBe true
			finally worker.foreach(_.close())

		"accept a SPARQL query POSTed with a non-strict (chunked) request entity" in:
			val baseUrl = s"http://127.0.0.1:${binding.localAddress.getPort}"
			val query = "SELECT ?o WHERE { GRAPH <urn:chunked:graph> { ?s ?p ?o } }"
			def chunked = HttpEntity.Chunked.fromData(
				ContentTypes.`text/plain(UTF-8)`,
				Source(query.grouped(8).map(ByteString.apply).toList)
			)

			def post(path: String, headers: Seq[HttpHeader]) =
				val resp = Await.result(
					Http().singleRequest(HttpRequest(HttpMethods.POST, s"$baseUrl$path", headers.toList, chunked)),
					10.seconds
				)
				val body = Await.result(resp.entity.toStrict(10.seconds), 10.seconds).data.utf8String
				resp.status -> body

			post("/internal/sparql", Nil)._1 shouldBe StatusCodes.OK
			post("/sparql", Seq(forwardedFor))._1 shouldBe StatusCodes.OK

		"terminate a long-running query with a bad-request response" in:
			val vf = repo.getValueFactory
			val graph = vf.createIRI("urn:slow:graph")
			val pred = vf.createIRI("urn:slow:p")
			repo.transact: conn =>
				for i <- 1 to 300 do conn.add(vf.createIRI(s"urn:slow:s$i"), pred, vf.createLiteral(i), graph)
			.isSuccess shouldBe true

			val longRunningQuery = """
				select ?a where {
					?a ?p1 ?b .
					?c ?p2 ?d .
					?e ?p3 ?f .
					filter(strlen(concat(str(?a), str(?c), str(?e))) > 100000)
				}
			"""
			val slowConf = sparqlConf.copy(maxQueryRuntimeSec = 1)
			val slowServer = new Rdf4jSparqlServer(repo, slowConf)
			try
				given ToResponseMarshaller[SparqlQuery] = slowServer.marshaller
				given RouteTestTimeout = RouteTestTimeout(20.seconds)
				val slowRoute = Route(
					repo,
					slowConf,
					DerivedMetadataService.unavailable(vf),
					message => Future.successful(s"read-only: $message")
				)
				val origin = "https://example.icos-cp.eu"
				Post("/sparql", HttpEntity(ContentTypes.`text/plain(UTF-8)`, longRunningQuery))
					.withHeaders(Origin(HttpOrigin(origin)), forwardedFor) ~> slowRoute ~> check:
						status shouldBe StatusCodes.BadRequest
						header(`Access-Control-Allow-Origin`.name).map(_.value) shouldBe Some(origin)
			finally slowServer.shutdown()

	override protected def afterAll(): Unit =
		Await.result(binding.unbind(), 5.seconds)
		sparqlServer.shutdown()
		repo.shutDown()
		super.afterAll()
