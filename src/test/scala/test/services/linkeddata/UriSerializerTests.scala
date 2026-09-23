package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ContentTypes, MediaTypes, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.{CitationStyle, PlainDoiCiter}
import se.lu.nateko.cp.meta.services.linkeddata.{InstanceServerSerializer, Rdf4jUriSerializer}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}

import scala.util.{Try, Using}

/** Characterizes the HTTP behavior of the original, pre-LandingPageBuilder URI serializer. */
class UriSerializerTests extends AnyFunSpec with ScalatestRouteTest:

	private val config = ConfigLoader.default
	private given Envri = Envri.ICOS
	private given EnvriConfigs = config.core.envriConfigs
	private val repo: Repository = SailRepository(MemoryStore())
	repo.init()
	Using.resources(
		getClass.getResourceAsStream("/linkeddata/landing-page-builder-fixture.trig"),
		repo.getConnection()
	): (stream, conn) =>
		conn.add(stream, "", RDFFormat.TRIG)

	private val vocab = CpVocab(repo.getValueFactory)
	private val metaVocab = CpmetaVocab(repo.getValueFactory)
	private val resource = repo.getValueFactory.createIRI("http://meta.icos-cp.eu/resources/test/serializer_test")
	private val referringResource = repo.getValueFactory.createIRI("http://meta.icos-cp.eu/resources/test/serializer_test_referrer")
	private val resourceUri = Uri(resource.stringValue)

	private val doiCiter = new PlainDoiCiter:
		def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]] = None
		def getDoiEager(doi: Doi): Option[Try[DoiMeta]] = None

	private val serializer = new Rdf4jUriSerializer(
		repo,
		vocab,
		metaVocab,
		MetaDb.getLenses(config.instanceServers, config.dataUploadService),
		doiCiter,
		config
	)

	private given ToResponseMarshaller[Uri] = serializer.marshaller

	private val graph = repo.getValueFactory.createIRI("http://meta.icos-cp.eu/resources/icos/")
	private val predicate = repo.getValueFactory.createIRI("http://example.org/refersTo")
	private val missingObjectHash = Sha256Sum.fromBytes(Array.fill(18)(0.toByte)).get
	private val missingObjectUri = Uri(s"https://meta.icos-cp.eu/objects/${missingObjectHash.id}")

	Using.resource(repo.getConnection()): conn =>
		conn.add(resource, RDFS.LABEL, vocab.lit("Serializer test resource"), graph)
		conn.add(resource, RDFS.COMMENT, vocab.lit("Serializer test comment"), graph)
		conn.add(referringResource, predicate, resource, graph)

	private def serialize(uri: Uri): Route = get:
		complete(uri)

	private def renderLandingPage(uri: Uri): String =
		var body = ""
		Get() ~> Accept(MediaTypes.`text/html`) ~> serialize(uri) ~> check:
			body = responseAs[String]
			assert(status === StatusCodes.OK, body)
			assert(contentType.mediaType === MediaTypes.`text/html`)
		body

	describe("an unknown object URI"):
		it("returns the original HTML not-found page"):
			Get() ~> Accept(MediaTypes.`text/html`) ~> serialize(missingObjectUri) ~> check:
				assert(status === StatusCodes.NotFound)
				assert(contentType.mediaType === MediaTypes.`text/html`)
				assert(responseAs[String].contains("Data object not found"))

		it("returns an error response for JSON because the RDF read produced errors"):
			Get() ~> Accept(MediaTypes.`application/json`) ~> serialize(missingObjectUri) ~> check:
				assert(status === StatusCodes.InternalServerError)
				assert(contentType === ContentTypes.`text/plain(UTF-8)`)
				assert(responseAs[String].nonEmpty)

	describe("a labeled resource URI"):
		it("renders the generic resource page as HTML"):
			Get() ~> Accept(MediaTypes.`text/html`) ~> serialize(resourceUri) ~> check:
				assert(status === StatusCodes.OK, responseAs[String])
				assert(contentType.mediaType === MediaTypes.`text/html`)
				val body = responseAs[String]
				assert(body.contains("Serializer test resource"))
				assert(body.contains("Serializer test comment"))

		it("returns its labeled-resource representation as JSON"):
			Get() ~> Accept(MediaTypes.`application/json`) ~> serialize(resourceUri) ~> check:
				assert(status === StatusCodes.OK, responseAs[String])
				assert(contentType === ContentTypes.`application/json`)
				val body = responseAs[String]
				assert(body.contains(resource.stringValue))
				assert(body.contains("Serializer test resource"))

		it("serializes both outgoing and incoming statements as RDF"):
			Get() ~> Accept(MediaTypes.`text/plain`) ~> serialize(resourceUri) ~> check:
				assert(status === StatusCodes.OK)
				assert(contentType === InstanceServerSerializer.turtleContType)
				val body = responseAs[String]
				assert(body.contains("Serializer test resource"))
				assert(body.contains(referringResource.stringValue))
				assert(body.contains(predicate.stringValue))

	describe("landing page URIs"):
		it("renders the data object landing page as HTML"):
			val body = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(body.contains("test_data.csv"))
			assert(body.contains("Test time series"))
			assert(body.contains("12345"))
			assert(body.contains("100"))
			assert(body.contains("Test station"))
			assert(body.contains("50.0"))
			assert(body.contains("Test instrument"))

		it("renders the document object landing page as HTML"):
			val body = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"))
			assert(body.contains("Test document"))
			assert(body.contains("test_doc.pdf"))
			assert(body.contains("54321"))
			assert(body.contains("Test Person"))
			assert(body.contains("Carbon Portal"))

		it("renders the collection landing page as HTML"):
			val body = renderLandingPage(Uri("https://meta.icos-cp.eu/collections/AwMDAwMDAwMDAwMDAwMDAwMD"))
			assert(body.contains("Test collection"))
			assert(body.contains("A collection of test items"))
			assert(body.contains("Carbon Portal"))
			assert(body.contains("test_data.csv"))
			assert(body.contains("Test document"))

		it("renders the station landing page as HTML"):
			val body = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/stations/TST"))
			assert(body.contains("Test station"))
			assert(body.contains("TST"))
			assert(body.contains("56.1"))
			assert(body.contains("13.4"))
			assert(body.contains("150 m"))
			assert(body.contains("Sweden"))

		it("renders the organization landing page as HTML"):
			val body = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/organizations/CP"))
			assert(body.contains("Carbon Portal"))
			assert(body.contains("CP"))

		it("renders the instrument landing page as HTML"):
			val body = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/instruments/TST_1"))
			assert(body.contains("Test instrument"))
			assert(body.contains("Picarro G2401"))
			assert(body.contains("SN-1"))
			assert(body.contains("Carbon Portal"))

		it("renders the person landing page as HTML"):
			val body = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/people/Test_Person"))
			assert(body.contains("Test Person"))
			assert(body.contains("TST"))
			assert(body.contains("PI"))
			assert(body.contains("2021-01-01"))

		it("renders the object specification landing page as HTML"):
			val body = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/cpmeta/testTimeSeries"))
			assert(body.contains("Test time series"))
			assert(body.contains("ICOS"))
			assert(body.contains("Atmosphere"))
			assert(body.contains("ASCII CSV time series"))
			assert(body.contains("plain text"))
			assert(body.contains("2"))

		it("renders the labeled resource landing page as HTML"):
			val body = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/themes/atmosphere"))
			assert(body.contains("Atmosphere"))
			assert(body.contains("https://static.icos-cp.eu/atmosphere.svg"))

	override def afterAll(): Unit =
		repo.shutDown()
		super.afterAll()
