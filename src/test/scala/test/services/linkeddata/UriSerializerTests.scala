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

	// the statistics services are not running; with the default exponential backoff after each refused
	// connection, every page rendered later in the suite would get slower and eventually time out
	override def testConfigSource =
		"""akka.http.host-connection-pool {
			base-connection-backoff = 10ms
			max-connection-backoff = 20ms
		}"""

	private val config = ConfigLoader.default
	private given Envri = Envri.ICOS
	private given EnvriConfigs = config.core.envriConfigs
	private val fixtureRepo: Repository = SailRepository(MemoryStore())
	private val counter = QueryCounter()
	private val repo: Repository = CountingRepository(fixtureRepo, counter)
	fixtureRepo.init()
	Using.resources(
		getClass.getResourceAsStream("/linkeddata/landing-page-builder-fixture.trig"),
		fixtureRepo.getConnection()
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

	private def renderLandingPage(uri: Uri): (Document, QueryCounts) =
		counter.reset()
		var page: Document = null
		Get() ~> Accept(MediaTypes.`text/html`) ~> serialize(uri) ~> check:
			val body = responseAs[String]
			assert(status === StatusCodes.OK, body)
			assert(contentType.mediaType === MediaTypes.`text/html`)
			page = Jsoup.parse(body)
		page -> counter.snapshot

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

	private def propertyLinks(page: Document, label: String): Seq[RenderedLink] =
		page.select("label.fw-bold").asScala.filter(_.text == label).toSeq
			.flatMap(_.parent.nextElementSibling.select("a").asScala)
			.map(link => RenderedLink(link.text, link.attr("href")))

	private def propertyText(page: Document, label: String): String = property(page, label).text

	private def propertyLink(page: Document, label: String): RenderedLink =
		val link = Option(property(page, label).selectFirst("a"))
			.getOrElse(fail(s"Property '$label' has no link"))
		RenderedLink(link.text, link.attr("href"))

	private def linkedLabelProperty(page: Document, labelHref: String): RenderedLink =
		val link = Option(propertyWithLinkedLabel(page, labelHref).selectFirst("a"))
			.getOrElse(fail(s"Property '$labelHref' has no value link"))
		RenderedLink(link.text, link.attr("href"))

	private def tableAfterHeading(page: Document, heading: String): Element =
		page.select("h2").asScala.find(_.text == heading)
			.flatMap(h2 => Option(h2.nextElementSibling).map(sibling => if sibling.tagName == "table" then sibling else sibling.selectFirst("table")))
			.getOrElse(fail(s"Missing table after heading '$heading'"))

	private def previewableVariablesTable(page: Document): Element = tableAfterHeading(page, "Previewable variables")

	private def tableRows(table: Element): Seq[Seq[Element]] =
		table.selectFirst("tbody").children.asScala.map(_.children.asScala.toSeq).toSeq

	private def metadataErrors(page: Document): Seq[String] =
		page.select("div.alert.d-flex > div").asScala.map(_.text).toSeq

	private def link(elem: Element): RenderedLink = RenderedLink(elem.text, elem.attr("href"))

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
			val (page, counts) = renderLandingPage(resourceUri)
			assert(counts === QueryCounts(connections = 3, statements = 0, existence = 2, sparql = 2))
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
			val (page, counts) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(counts === QueryCounts(connections = 1, statements = 310, existence = 11, sparql = 0))
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

		it("renders the previewable variables of the data object landing page"):
			val (page, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			val table = previewableVariablesTable(page)
			val headers = table.selectFirst("thead > tr").children.asScala.map(_.text)
			assert(headers === Seq("Name", "Value type", "Unit", "Quantity kind", "Preview", "Instrument Deployments"))

			val rows = table.selectFirst("tbody").children.asScala.map(_.children.asScala.toSeq).toSeq
			assert(rows.map(_.take(5).map(_.text)) === Seq(
				Seq("TIMESTAMP", "time instant, UTC", "", "", ""),
				Seq("co2", "CO2 mixing ratio (dry mole fraction)", "µmol mol-1", "portion", "Preview"),
				Seq("ch4", "CH4 mixing ratio (dry mole fraction)", "nmol mol-1", "portion", "Preview")
			))

			val previewLinks = rows.map(cells => Option(cells(4).selectFirst("a")).map(_.attr("href")))
			def previewUrl(variable: String) =
				s"https://data.icos-cp.eu/portal/#%7B%22route%22:%22preview%22,%22preview%22:%5B%22AQEBAQEBAQEBAQEBAQEBAQEB%22%5D,%22yAxis%22:%22$variable%22%7D"
			assert(previewLinks === Seq(None, Some(previewUrl("co2")), Some(previewUrl("ch4"))))

			// only the co2 deployment that overlaps the acquisition interval is shown
			val deploymentCells = rows.map(_(5))
			assert(deploymentCells(0).children.isEmpty)
			assert(deploymentCells(2).children.isEmpty)
			val deploymentRows = deploymentCells(1).select("table.instrument-deployment tbody tr").asScala
				.map(_.children.asScala.map(_.text).toSeq).toSeq
			assert(deploymentRows === Seq(Seq(
				"Start: 2020-06-01 00:00:00 Stop: Not done",
				"Latitude: 56.1 Longitude: 13.4 Altitude: 50.0 m",
				"Test instrument"
			)))
			val instrumentLink = deploymentCells(1).selectFirst("table.instrument-deployment tbody tr a")
			assert(RenderedLink(instrumentLink.text, instrumentLink.attr("href")) === RenderedLink("Test instrument", "/resources/instruments/TST_1"))

		it("renders the production of the data object landing page"):
			val (page, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(page.select("h2").asScala.map(_.text).contains("Production"))
			assert(propertyLink(page, "File made by") === RenderedLink("Test Person", "/resources/people/Test_Person"))
			assert(propertyLink(page, "Host organization") === RenderedLink("Atmosphere Thematic Centre", "/resources/organizations/ATC"))
			assert(propertyText(page, "Production time (UTC)") === "2022-01-01 12:00:00")
			assert(propertyText(page, "Comment") === "Test production comment")
			// rdf:Seq order, not sorted
			assert(propertyLinks(page, "Contributors") === Seq(
				RenderedLink("Zed Contributor", "/resources/people/Zed_Contributor"),
				RenderedLink("Atmosphere Thematic Centre", "/resources/organizations/ATC"),
				RenderedLink("Test Person", "/resources/people/Test_Person")
			))
			// sorted by name, read from the global graph view
			assert(propertyLinks(page, "Source object") === Seq(
				RenderedLink("previous_data.csv", "/objects/BAQEBAQEBAQEBAQEBAQEBAQE"),
				RenderedLink("versioned_data.csv", "/objects/BQUFBQUFBQUFBQUFBQUFBQUF")
			))

		it("lists the spec documentation before the production documentation on data object landing pages"):
			val (page, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(propertyLinks(page, "Documentation") === Seq(
				RenderedLink("test_time_series_description.pdf", "/objects/ERERERERERERERERERERERER"),
				RenderedLink("Test document", "/objects/AgICAgICAgICAgICAgICAgIC")
			))

		it("renders the version chain of a data object landing page"):
			val (page, counts) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/BQUFBQUFBQUFBQUFBQUFBQUF"))
			assert(counts === QueryCounts(connections = 1, statements = 268, existence = 24, sparql = 0))
			assert(propertyLink(page, "Previous version") === RenderedLink("View previous version", "/objects/BAQEBAQEBAQEBAQEBAQEBAQE"))
			// the incomplete and the under-moratorium next versions are ignored; the remaining one lives in another graph
			assert(propertyLink(page, "Next version") === RenderedLink("View next version", "/objects/BgYGBgYGBgYGBgYGBgYGBgYG"))
			// the latest version is reached through a plain collection that supersedes the next version
			val alert = Option(page.selectFirst(".alert-warning")).getOrElse(fail("Missing deprecation alert"))
			assert(alert.selectFirst(".alert-heading").text === "Deprecated data")
			val latestLinks = alert.select("a.alert-link").asScala.map(link => RenderedLink(link.text, link.attr("href"))).toSeq
			assert(latestLinks === Seq(
				RenderedLink("CQkJCQkJCQkJCQkJCQkJCQkJ", "/objects/CQkJCQkJCQkJCQkJCQkJCQkJ"),
				RenderedLink("CgoKCgoKCgoKCgoKCgoKCgoK", "/objects/CgoKCgoKCgoKCgoKCgoKCgoK")
			))
			assert(alert.text.contains("Latest versions:"))

		it("renders the document object landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"))
			assert(counts === QueryCounts(connections = 1, statements = 42, existence = 2, sparql = 0))
			assert(heading(page) === "Test document")
			assert(propertyText(page, "File name") === "test_doc.pdf")
			assert(propertyText(page, "File size") === "53 KB (54321 bytes)")
			assert(propertyLink(page, "Submitted by") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))
			assert(page.select("h2").asScala.map(_.text).contains("Submission"))
			assert(page.select("a[href='./AgICAgICAgICAgICAgICAgIC/test_doc.pdf.json']").size === 1)

		it("renders the creators of the document object landing page as authors"):
			val (page, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"))
			assert(propertyLinks(page, "Authors") === Seq(
				RenderedLink("Zed Contributor", "/resources/people/Zed_Contributor"),
				RenderedLink("Test Person", "/resources/people/Test_Person")
			))
			// the document is part of the test collection, but not of its superseded previous version
			assert(propertyLinks(page, "Part of") === Seq(RenderedLink("Test collection", "/collections/AwMDAwMDAwMDAwMDAwMDAwMD")))

		it("renders the collection landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("https://meta.icos-cp.eu/collections/AwMDAwMDAwMDAwMDAwMDAwMD"))
			assert(counts === QueryCounts(connections = 1, statements = 26, existence = 4, sparql = 0))
			assert(heading(page) === "Test collection")
			assert(propertyText(page, "Description") === "A collection of test items")
			assert(propertyLink(page, "Collection creator") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))
			assert(propertyText(page, "Number of items") === "3")
			val itemLinks = page.select("a[target=_blank]").asScala.map(link => link.text -> link.attr("href")).toSet
			assert(itemLinks.contains("test_data.csv" -> "https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(itemLinks.contains("Test document" -> "https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"))

		it("renders the nested collections and versions of the collection landing page"):
			val (page, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/collections/AwMDAwMDAwMDAwMDAwMDAwMD"))
			// members are sorted by name; the nested collection is listed by its title
			val itemLinks = page.select("a[target=_blank]").asScala.map(link => RenderedLink(link.text, link.attr("href"))).toSeq
			assert(itemLinks === Seq(
				RenderedLink("Nested collection", "https://meta.icos-cp.eu/collections/DAwMDAwMDAwMDAwMDAwMDAwM"),
				RenderedLink("Test document", "https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"),
				RenderedLink("test_data.csv", "https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB")
			))
			assert(propertyLink(page, "Previous version") === RenderedLink("View previous version", "/collections/DQ0NDQ0NDQ0NDQ0NDQ0NDQ0N"))
			assert(propertyLinks(page, "Next version").isEmpty)
			assert(propertyLinks(page, "Part of").isEmpty)
			assert(propertyLinks(page, "Documentation") === Seq(RenderedLink("Test document", "/objects/AgICAgICAgICAgICAgICAgIC")))

		it("renders the nested collection landing page"):
			val (page, counts) = renderLandingPage(Uri("https://meta.icos-cp.eu/collections/DAwMDAwMDAwMDAwMDAwMDAwM"))
			assert(counts === QueryCounts(connections = 1, statements = 29, existence = 6, sparql = 0))
			assert(heading(page) === "Nested collection")
			assert(propertyLinks(page, "Part of") === Seq(RenderedLink("Test collection", "/collections/AwMDAwMDAwMDAwMDAwMDAwMD")))
			assert(propertyLink(page, "Next version") === RenderedLink("View next version", "/collections/Dg4ODg4ODg4ODg4ODg4ODg4O"))
			val alert = Option(page.selectFirst(".alert-warning")).getOrElse(fail("Missing deprecation alert"))
			assert(alert.selectFirst(".alert-heading").text === "Deprecated collection")
			assert(Option(alert.selectFirst("a.alert-link")).map(link => RenderedLink(link.text, link.attr("href"))) ===
				Some(RenderedLink("Dg4ODg4ODg4ODg4ODg4ODg4O", "/collections/Dg4ODg4ODg4ODg4ODg4ODg4O")))
			val itemLinks = page.select("a[target=_blank]").asScala.map(link => RenderedLink(link.text, link.attr("href"))).toSeq
			assert(itemLinks === Seq(RenderedLink("versioned_data.csv", "https://meta.icos-cp.eu/objects/BQUFBQUFBQUFBQUFBQUFBQUF")))

		it("lists only the current parent collections on data object landing pages"):
			val (page, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"))
			assert(propertyLinks(page, "Part of") === Seq(RenderedLink("Test collection", "/collections/AwMDAwMDAwMDAwMDAwMDAwMD")))
			val (nestedMember, _) = renderLandingPage(Uri("https://meta.icos-cp.eu/objects/BQUFBQUFBQUFBQUFBQUFBQUF"))
			assert(propertyLinks(nestedMember, "Part of") === Seq(RenderedLink("Nested collection, version 2", "/collections/Dg4ODg4ODg4ODg4ODg4ODg4O")))

		it("renders the station landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/stations/TST"))
			assert(counts === QueryCounts(connections = 1, statements = 96, existence = 4, sparql = 0))
			assert(heading(page) === "Test station")
			assert(propertyText(page, "Station ID") === "TST")
			assert(propertyText(page, "Country code") === "SE")
			assert(propertyText(page, "Latitude/Longitude") === "56.1, 13.4")
			assert(propertyText(page, "Elevation") === "150 m")

		it("renders the sub-resources of the ICOS station landing page"):
			val (page, _) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/stations/TST"))
			assert(metadataErrors(page) === Nil)
			assert(propertyText(page, "Description") === "Test station description")
			assert(propertyText(page, "WIGOS ID") === "0-20008-0-TST")
			assert(propertyText(page, "ICOS Station class") === "1")
			assert(propertyText(page, "ICOS Labeling date") === "2019-06-01")
			assert(propertyText(page, "Time zone offset") === "1")
			assert(propertyLinks(page, "Associated networks") === Seq(RenderedLink("Test network", "/resources/networks/TestNet")))
			assert(propertyLinks(page, "Documentation") === Seq(RenderedLink("Test document", "/objects/AgICAgICAgICAgICAgICAgIC")))
			assert(propertyLinks(page, "Organization") === Seq(RenderedLink("Carbon Portal", "/resources/organizations/CP")))

			// sorted by end date, the ongoing funding last
			val fundingRows = tableRows(tableAfterHeading(page, "Acknowledgements"))
			assert(fundingRows.map(_.map(_.text)) === Seq(
				Seq("Swedish Research Council", "2019-001", "Early award", "2019-01-01", "2020-12-31", ""),
				Seq("Swedish Research Council", "", "Ongoing award", "2021-01-01", "", "Ongoing funding")
			))
			assert(link(fundingRows(0)(0).selectFirst("a")) === RenderedLink("Swedish Research Council", "/resources/organizations/VR"))
			assert(link(fundingRows(0)(1).selectFirst("a")) === RenderedLink("2019-001", "https://example.org/awards/2019-001"))
			assert(fundingRows(1)(2).select("a").isEmpty)

			// the spatial coverage makes the location section with its map appear
			assert(page.select("h2").asScala.map(_.text).contains("Location"))
			assert(page.select("iframe").asScala.map(_.attr("src")).toSeq === Seq("/station/?station=/resources/stations/TST&icon="))
			assert(page.select("img.img-fluid").asScala.map(_.attr("src")).toSeq === Seq("https://static.icos-cp.eu/images/stations/TST.jpg"))

		it("renders the ecosystem station landing page with webpage elements"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/stations/ES_TST"))
			assert(counts === QueryCounts(connections = 1, statements = 48, existence = 2, sparql = 0))
			assert(metadataErrors(page) === Nil)
			assert(heading(page) === "ICOS STATION Test ecosystem station")
			assert(page.selectFirst(".wide-cover-image").attr("style").contains("https://static.icos-cp.eu/images/stations/ES_TST_cover.jpg"))
			assert(page.text.contains("Welcome to the test ecosystem station"))
			val linkBoxes = page.select("h3.h6 a").asScala.map(link).toSeq
			assert(linkBoxes === Seq(
				RenderedLink("Station news", "https://example.org/es_tst/news"),
				RenderedLink("Station data", "https://example.org/es_tst/data")
			))
			assert(page.select("h2").asScala.map(_.text).contains("Detailed information"))
			assert(propertyLinks(page, "Climate zone") === Seq(RenderedLink("Dfc - Subarctic", "/resources/climateZones/Dfc")))
			assert(propertyLinks(page, "Main ecosystem") === Seq(RenderedLink("ENF - Evergreen Needleleaf Forests", "/resources/ecosystems/ENF")))
			assert(propertyText(page, "Mean annual temperature") === "1.8 °C")
			assert(propertyText(page, "Mean annual precipitation") === "614.0 mm")
			assert(propertyText(page, "Mean annual incoming SW radiation") === "90.5 W/m2")
			assert(propertyLinks(page, "Documentation resource") === Seq(RenderedLink("https://example.org/es_tst/docs", "https://example.org/es_tst/docs")))
			assert(propertyLinks(page, "Data publication") === Seq(RenderedLink("https://doi.org/10.1234/es_tst", "https://doi.org/10.1234/es_tst")))
			assert(propertyText(page, "Latitude/Longitude") === "64.25, 19.77")
			assert(propertyText(page, "Elevation") === "235 m")

		it("renders the SITES station landing page"):
			val sitesStation = Uri("https://meta.fieldsites.se/resources/stations/Testsjon")
			val (page, counts) = renderLandingPage(sitesStation)
			assert(counts === QueryCounts(connections = 1, statements = 55, existence = 1, sparql = 0))
			assert(metadataErrors(page) === Nil)
			assert(heading(page) === "Testsjön Research Station")
			assert(propertyText(page, "Station ID") === "TSJ")
			assert(propertyLinks(page, "Main ecosystems") === Seq(
				RenderedLink("Forest", "/resources/ecosystems/forest"),
				RenderedLink("Lake", "/resources/ecosystems/lake")
			))
			assert(propertyLinks(page, "Climate zone") === Seq(RenderedLink("Dfb - Warm-summer humid continental", "/resources/climateZones/Dfb")))
			assert(propertyText(page, "Mean annual temperature") === "5.5 °C")
			assert(propertyText(page, "Operational period") === "2015-")
			assert(propertyLinks(page, "Documentation") === Seq(RenderedLink("testsjon_description.pdf", "/objects/EBAQEBAQEBAQEBAQEBAQEBAQ")))

		it("returns the sites of the SITES station, with their ecosystems and coverages, as JSON"):
			Get() ~> Accept(MediaTypes.`application/json`) ~> serialize(Uri("https://meta.fieldsites.se/resources/stations/Testsjon")) ~> check:
				assert(status === StatusCodes.OK, responseAs[String])
				val body = responseAs[String]
				Seq("Testsjön forest", "Forest mast", "Testsjön lake", "Lake outline", "Polygon").foreach: expected =>
					assert(body.contains(expected), s"'$expected' missing in $body")

		it("renders the organization landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/organizations/CP"))
			assert(counts === QueryCounts(connections = 1, statements = 7, existence = 0, sparql = 0))
			assert(heading(page) === "Carbon Portal (CP)")
			assert(propertyText(page, "Name") === "Carbon Portal")

		it("renders the instrument landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/instruments/TST_1"))
			assert(counts === QueryCounts(connections = 1, statements = 66, existence = 1, sparql = 0))
			assert(heading(page) === "Test instrument")
			assert(propertyText(page, "Model") === "Picarro G2401")
			assert(propertyText(page, "Serial number") === "SN-1")
			assert(propertyLink(page, "Owner") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))

		it("renders the vendor, components and deployments of the instrument landing page"):
			val (page, _) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/instruments/TST_1"))
			assert(metadataErrors(page) === Nil)
			assert(propertyLink(page, "Vendor") === RenderedLink("Picarro Inc.", "/resources/organizations/Picarro"))
			assert(propertyLinks(page, "Has component") === Seq(RenderedLink("Nafion dryer (SN-2)", "/resources/instruments/TST_2")))
			assert(propertyLinks(page, "Is part of").isEmpty)
			assert(propertyText(page, "Comment") === "Main analyser")
			// all deployments are listed, regardless of any acquisition interval
			val deploymentRows = tableRows(tableAfterHeading(page, "Deployments"))
			assert(deploymentRows.map(_.map(_.text)).sortBy(_(6)) === Seq(
				Seq("co2", "CO2 column", "Test station", "", "", "", "2018-01-01 00:00:00", "2019-01-01 00:00:00"),
				Seq("co2", "CO2 column", "Test station", "56.1", "13.4", "50.0 m", "2020-06-01 00:00:00", "")
			))
			val deploymentLinks = deploymentRows.head.flatMap(_.select("a").asScala.map(link))
			assert(deploymentLinks === Seq(
				RenderedLink("CO2 column", "/resources/cpmeta/testTimeSeriesDataset_co2"),
				RenderedLink("Test station", "/resources/stations/TST")
			))

		it("renders the instrument component landing page"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/instruments/TST_2"))
			assert(counts === QueryCounts(connections = 1, statements = 16, existence = 1, sparql = 0))
			assert(heading(page) === "Nafion dryer (SN-2)")
			assert(propertyLinks(page, "Is part of") === Seq(RenderedLink("Test instrument", "/resources/instruments/TST_1")))
			assert(page.select("h2").asScala.map(_.text).contains("Deployments") === false)

		it("renders the person landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/people/Test_Person"))
			assert(counts === QueryCounts(connections = 1, statements = 17, existence = 0, sparql = 0))
			assert(heading(page) === "Test Person")
			assert(propertyText(page, "First name") === "Test")
			assert(propertyText(page, "Last name") === "Person")
			val roleCells = page.select("table tbody tr").asScala.flatMap(_.select("td").asScala.map(_.text))
			assert(roleCells === Seq("PI", "TST", "2021-01-01", ""))

		it("renders the object specification landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/cpmeta/testTimeSeries"))
			assert(counts === QueryCounts(connections = 2, statements = 0, existence = 1, sparql = 2))
			assert(heading(page) === "Test time series")
			assert(propertyText(page, "Label") === "Test time series")
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasAssociatedProject") === RenderedLink("ICOS", "/resources/projects/icos"))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasDataTheme") === RenderedLink("Atmosphere", "/resources/themes/atmosphere"))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasFormat") === RenderedLink("ASCII CSV time series", "/ontologies/cpmeta/csvWithIso8601tsFirstCol"))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasEncoding") === RenderedLink("plain text", "/ontologies/cpmeta/asciiEncoding"))
			assert(propertyWithLinkedLabel(page, "/ontologies/cpmeta/hasDataLevel").text === "2")
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasDocumentationObject") ===
				RenderedLink("https://meta.icos-cp.eu/objects/ERERERERERERERERERERERER", "/objects/ERERERERERERERERERERERER"))

		it("renders the labeled resource landing page as HTML"):
			val (page, counts) = renderLandingPage(Uri("http://meta.icos-cp.eu/resources/themes/atmosphere"))
			assert(counts === QueryCounts(connections = 3, statements = 0, existence = 2, sparql = 2))
			assert(heading(page) === "Atmosphere")
			assert(propertyText(page, "Label") === "Atmosphere")
			assert(propertyWithLinkedLabel(page, "/ontologies/cpmeta/hasIcon").text === "https://static.icos-cp.eu/atmosphere.svg")

	override def afterAll(): Unit =
		fixtureRepo.shutDown()
		super.afterAll()
