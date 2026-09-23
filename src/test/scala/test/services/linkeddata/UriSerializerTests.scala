package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ContentTypes, MediaTypes, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import eu.icoscp.envri.Envri
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
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

import scala.jdk.CollectionConverters.*
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

	private def renderLandingPage(uri: Uri): Document =
		var page: Document = null
		Get() ~> Accept(MediaTypes.`text/html`) ~> serialize(uri) ~> check:
			val body = responseAs[String]
			assert(status === StatusCodes.OK, body)
			assert(contentType.mediaType === MediaTypes.`text/html`)
			page = Jsoup.parse(body)
		page

	private case class RenderedLink(text: String, href: String)

	private def heading(page: Document): String =
		Option(page.selectFirst("h1")).map(_.text).getOrElse(fail("Missing heading"))

	private def property(page: Document, label: String): Element =
		page.select("label.fw-bold").asScala.find(_.text == label)
			.map(_.parent.nextElementSibling)
			.getOrElse(fail(s"Missing property '$label'"))

	private def propertyWithLinkedLabel(page: Document, href: String): Element =
		page.select("label.fw-bold a").asScala.find(_.attr("href") == href)
			.map(_.parent.parent.nextElementSibling)
			.getOrElse(fail(s"Missing property with link '$href'; found ${page.select("label.fw-bold a").eachAttr("href")}"))

	private def propertyText(page: Document, label: String): String = property(page, label).text

	private def propertyLink(page: Document, label: String): RenderedLink =
		val link = Option(property(page, label).selectFirst("a"))
			.getOrElse(fail(s"Property '$label' has no link"))
		RenderedLink(link.text, link.attr("href"))

	private def linkedLabelProperty(page: Document, labelHref: String): RenderedLink =
		val link = Option(propertyWithLinkedLabel(page, labelHref).selectFirst("a"))
			.getOrElse(fail(s"Property '$labelHref' has no value link"))
		RenderedLink(link.text, link.attr("href"))

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
			val page = renderLandingPage(resourceUri)
			assert(heading(page) === "Serializer test resource")
			assert(propertyText(page, "URI") === resource.stringValue)
			assert(propertyText(page, "Label") === "Serializer test resource")
			assert(propertyText(page, "Comment") === "Serializer test comment")
			val usage = page.selectFirst("label.fw-bold a[href='/resources/test/serializer_test_referrer']")
			assert(Option(usage).map(_.text) === Some("http://meta.icos-cp.eu/resources/test/serializer_test_referrer"))

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
			val page = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(heading(page) === "Test time series from Test station (50.0 m)")
			assert(propertyText(page, "File name") === "test_data.csv")
			assert(propertyText(page, "File size") === "12 KB (12345 bytes)")
			assert(propertyText(page, "Number of data rows") === "100")
			assert(propertyText(page, "Data level") === "2")
			assert(propertyText(page, "Sampling height") === "50.0")
			assert(propertyLink(page, "Data type") === RenderedLink("Test time series", "/resources/cpmeta/testTimeSeries"))
			assert(propertyLink(page, "Station") === RenderedLink("Test station", "/resources/stations/TST"))
			assert(propertyLink(page, "Instrument") === RenderedLink("Test instrument", "/resources/instruments/TST_1"))
			assert(page.select("h2").asScala.map(_.text).contains("Acquisition"))
			assert(page.select("h2").asScala.map(_.text).contains("Technical information"))

		it("renders the document object landing page as HTML"):
			val page = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"))
			assert(heading(page) === "Test document")
			assert(propertyText(page, "File name") === "test_doc.pdf")
			assert(propertyText(page, "File size") === "53 KB (54321 bytes)")
			assert(propertyLink(page, "Submitted by") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))
			assert(page.select("h2").asScala.map(_.text).contains("Submission"))
			assert(page.select("a[href='./AgICAgICAgICAgICAgICAgIC/test_doc.pdf.json']").size === 1)

		it("renders the collection landing page as HTML"):
			val page = renderLandingPage(Uri("https://meta.icos-cp.eu/collections/AwMDAwMDAwMDAwMDAwMDAwMD"))
			assert(heading(page) === "Test collection")
			assert(propertyText(page, "Description") === "A collection of test items")
			assert(propertyLink(page, "Collection creator") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))
			assert(propertyText(page, "Number of items") === "2")
			val itemLinks = page.select("a[target=_blank]").asScala.map(link => link.text -> link.attr("href")).toSet
			assert(itemLinks.contains("test_data.csv" -> "https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(itemLinks.contains("Test document" -> "https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"))

		it("renders the station landing page as HTML"):
			val page = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/stations/TST"))
			assert(heading(page) === "Test station")
			assert(propertyText(page, "Station ID") === "TST")
			assert(propertyText(page, "Country code") === "SE")
			assert(propertyText(page, "Latitude/Longitude") === "56.1, 13.4")
			assert(propertyText(page, "Elevation") === "150 m")

		it("renders the organization landing page as HTML"):
			val page = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/organizations/CP"))
			assert(heading(page) === "Carbon Portal (CP)")
			assert(propertyText(page, "Name") === "Carbon Portal")

		it("renders the instrument landing page as HTML"):
			val page = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/instruments/TST_1"))
			assert(heading(page) === "Test instrument")
			assert(propertyText(page, "Model") === "Picarro G2401")
			assert(propertyText(page, "Serial number") === "SN-1")
			assert(propertyLink(page, "Owner") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))

		it("renders the person landing page as HTML"):
			val page = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/people/Test_Person"))
			assert(heading(page) === "Test Person")
			assert(propertyText(page, "First name") === "Test")
			assert(propertyText(page, "Last name") === "Person")
			val roleCells = page.select("table tbody tr").asScala.flatMap(_.select("td").asScala.map(_.text))
			assert(roleCells === Seq("PI", "TST", "2021-01-01", ""))

		it("renders the object specification landing page as HTML"):
			val page = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/cpmeta/testTimeSeries"))
			assert(heading(page) === "Test time series")
			assert(propertyText(page, "Label") === "Test time series")
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasAssociatedProject") === RenderedLink("ICOS", "/resources/projects/icos"))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasDataTheme") === RenderedLink("Atmosphere", "/resources/themes/atmosphere"))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasFormat") === RenderedLink("ASCII CSV time series", "/ontologies/cpmeta/csvWithIso8601tsFirstCol"))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasEncoding") === RenderedLink("plain text", "/ontologies/cpmeta/asciiEncoding"))
			assert(propertyWithLinkedLabel(page, "/ontologies/cpmeta/hasDataLevel").text === "2")

		it("renders the labeled resource landing page as HTML"):
			val page = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/themes/atmosphere"))
			assert(heading(page) === "Atmosphere")
			assert(propertyText(page, "Label") === "Atmosphere")
			assert(propertyWithLinkedLabel(page, "/ontologies/cpmeta/hasIcon").text === "https://static.icos-cp.eu/atmosphere.svg")

	override def afterAll(): Unit =
		repo.shutDown()
		super.afterAll()
