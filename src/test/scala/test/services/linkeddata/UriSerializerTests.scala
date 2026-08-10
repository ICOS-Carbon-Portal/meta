package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ContentType, ContentTypes, MediaType, MediaTypes, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import eu.icoscp.envri.Envri
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.AttributionProvider
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataClient
import se.lu.nateko.cp.meta.services.linkeddata.{InstanceServerSerializer, LandingPageLoader, Rdf4jUriSerializer}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.test.TestConfig

import scala.jdk.CollectionConverters.*

/**
 * Characterizes the HTTP behavior of the URI serializer: content negotiation, status codes and the
 * choice of page, all of which it still owns after handing the reading of every page to the
 * LandingPageLoader.
 *
 * Every landing-page test also builds the same page with the LandingPageLoader and puts the query
 * counts of the two read paths next to each other. Both read the same fixture through a fresh
 * counting view of it, which counts every statement lookup, existence check and SPARQL query made
 * through it, so the two numbers are directly comparable. What the loader makes of the pages is not
 * asserted here; that is [[LandingPageLoaderTests]]. A change in either count means a read path
 * changed, and the new number has to be looked at (and only then written down here) rather than
 * silently accepted.
 *
 * Every real landing page costs the serializer exactly what it costs the loader: those URIs are
 * recognized by their shape alone, and the page is whatever the loader built. The URIs that have
 * no page of their own are recognized by probing the store instead (see [[probeCounts]]), and
 * their counts are stated as the sum of that probing and the read that follows it, so that
 * nothing is left unaccounted for.
 */
class UriSerializerTests extends AnyFunSpec with ScalatestRouteTest {

	// the statistics services are not running; with the default exponential backoff after each refused
	// connection, every page rendered later in the suite would get slower and eventually time out
	override def testConfigSource =
		"""akka.http.host-connection-pool {
			base-connection-backoff = 10ms
			max-connection-backoff = 20ms
		}"""

	private val repo = Fixture.createRepo()

	override def afterAll(): Unit = {
		repo.shutDown()
		super.afterAll()
	}

	describe("an unknown object URI") {
		it("returns the original HTML not-found page") {
			val (route, _counts) = serialize(Fixture.missingObjectUri)
			Get() ~> Accept(MediaTypes.`text/html`) ~> route ~> check {
				assert(status === StatusCodes.NotFound)
				assert(contentType.mediaType === MediaTypes.`text/html`)
				assert(responseAs[String].contains("Data object not found"))
			}
		}

		it("returns an error response for JSON because the RDF read produced errors") {
			val (route, _counts) = serialize(Fixture.missingObjectUri)
			Get() ~> Accept(MediaTypes.`application/json`) ~> route ~> check {
				assert(status === StatusCodes.InternalServerError)
				assert(contentType === ContentTypes.`text/plain(UTF-8)`)
				assert(responseAs[String].nonEmpty)
			}
		}

		it("returns an RDF document with only the namespace prefixes") {
			val (body, counts) =
				renderRdf(Fixture.missingObjectUri, MediaTypes.`text/plain`, InstanceServerSerializer.turtleContType)
			// includes the failed static object fetch, which runs before the RDF fallback is chosen
			assert(counts === QueryCounts(connections = 3, statements = 3, existence = 0, sparql = 0))
			assert(body.linesIterator.map(_.trim).forall(line => line.isEmpty || line.startsWith("@prefix")), body)
		}
	}

	describe("a labeled resource URI") {
		it("renders the generic resource page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.resourceUri)
			assert(counts === QueryCounts(connections = 3, statements = 0, existence = 2, sparql = 2))
			//all the serializer adds to the loader is the probing it does to pick the page
			assert(counts === probeCounts(Fixture.resourceUri) + loadGenericResource(Fixture.resourceUri))
			assert(heading(page) === "Serializer test resource")
			assert(propertyText(page, "URI") === Fixture.resource.stringValue)
			assert(propertyText(page, "Label") === "Serializer test resource")
			assert(propertyText(page, "Comment") === "Serializer test comment")
			val usage = page.selectFirst("label.fw-bold a[href='/resources/test/serializer_test_referrer']")
			assert(Option(usage).map(_.text) === Some("http://meta.icos-cp.eu/resources/test/serializer_test_referrer"))
		}

		it("returns its labeled-resource representation as JSON") {
			val (body, counts) = renderJson(Fixture.resourceUri)
			assert(counts === QueryCounts(connections = 3, statements = 1, existence = 2, sparql = 0))
			assert(counts === probeCounts(Fixture.resourceUri) + loadLandingPage(_.labeledResource(Fixture.resourceUri)))
			assert(body.contains(Fixture.resource.stringValue))
			assert(body.contains("Serializer test resource"))
		}

		it("serializes both outgoing and incoming statements as RDF") {
			val (body, counts) = renderRdf(Fixture.resourceUri, MediaTypes.`text/plain`, InstanceServerSerializer.turtleContType)
			// the resource type checks run before the RDF fallback is chosen
			assert(counts === QueryCounts(connections = 4, statements = 2, existence = 2, sparql = 0))
			assert(body.contains("Serializer test resource"))
			assert(body.contains(Fixture.referringResource.stringValue))
			assert(body.contains(Fixture.predicate.stringValue))
		}

		Seq(
			MediaTypes.`text/plain` -> InstanceServerSerializer.turtleContType,
			InstanceServerSerializer.turtleContType.mediaType -> InstanceServerSerializer.turtleContType,
			MediaTypes.`application/xml` -> InstanceServerSerializer.xmlContType,
			InstanceServerSerializer.xmlContType.mediaType -> InstanceServerSerializer.xmlContType
		).foreach { (accepted, returned) =>
			it(s"serializes the statements as ${returned.mediaType} when accepting $accepted") {
				val (body, _counts) = renderRdf(Fixture.resourceUri, accepted, returned)
				assert(body.contains("Serializer test resource"))
				assert(body.contains(Fixture.referringResource.stringValue))
			}
		}
	}

	describe("data object landing pages") {
		it("falls back to serializing the data object statements as RDF") {
			val (body, counts) =
				renderRdf(Fixture.timeSeriesObject, MediaTypes.`text/plain`, InstanceServerSerializer.turtleContType)
			// includes the full static object fetch for the landing page, which runs before the RDF fallback is chosen
			assert(counts === QueryCounts(connections = 3, statements = 56, existence = 0, sparql = 0))
			assert(body.contains("test_data.csv"))
		}

		it("renders the data object landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.timeSeriesObject)
			assert(counts === QueryCounts(connections = 1, statements = 54, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.staticObject(Fixture.timeSeriesHash)))
			//the citation-derived title now comes from rdfStore, which is not running in this test
			assert(heading(page) === "test_data.csv")
			assert(propertyText(page, "File name") === "test_data.csv")
			assert(propertyText(page, "File size") === "12 KB (12345 bytes)")
			assert(propertyText(page, "Number of data rows") === "100")
			assert(propertyText(page, "Data level") === "2")
			assert(propertyText(page, "Sampling height") === "50.0")
			assert(propertyLink(page, "Data type") === RenderedLink(
				"Test time series",
				"/resources/cpmeta/testTimeSeries"
			))
			val station = RenderedLink("Test station", "/resources/stations/TST")
			assert(propertyLink(section(page, "Acquisition"), "Station") === station)
			assert(propertyLink(mapCard(page), "Station") === station)
			assert(propertyLinks(page, "Station").size === 2)
			assert(propertyLink(page, "Instrument") === RenderedLink("Test instrument", "/resources/instruments/TST_1"))
			assert(sectionHeadings(page).contains("Acquisition"))
			assert(sectionHeadings(page).contains("Technical information"))
		}

		it("renders the previewable variables of the data object landing page") {
			val (page, _) = renderLandingPage(Fixture.timeSeriesObject)
			val table = tableAfterHeading(page, "Previewable variables")
			assert(table.select("> thead > tr > th").asScala.map(_.text) === Seq(
				"Name", "Value type", "Unit", "Quantity kind", "Preview", "Instrument Deployments"
			))

			// only the co2 deployment that overlaps the acquisition interval is shown
			val rows = tableRows(table)
			assert(rows.map(_.map(_.text)) === Seq(
				Seq("TIMESTAMP", "time instant, UTC", "", "", "", ""),
				Seq("co2", "CO2 mixing ratio (dry mole fraction)", "µmol mol-1", "portion", "Preview",
					"Time interval Position Instrument " +
					"Start: 2020-06-01 00:00:00 Stop: Not done Latitude: 56.1 Longitude: 13.4 Altitude: 50.0 m Test instrument"),
				Seq("ch4", "CH4 mixing ratio (dry mole fraction)", "nmol mol-1", "portion", "Preview", "")
			))

			def preview(variable: String) = RenderedLink(
				"Preview",
				s"https://data.icos-cp.eu/portal/#%7B%22route%22:%22preview%22,%22preview%22:%5B%22AQEBAQEBAQEBAQEBAQEBAQEB%22%5D,%22yAxis%22:%22$variable%22%7D"
			)
			assert(rows.map(_.flatMap(links(_))) === Seq(
				Seq(),
				Seq(preview("co2"), RenderedLink("Test instrument", "/resources/instruments/TST_1")),
				Seq(preview("ch4"))
			))
		}

		it("renders the production of the data object landing page") {
			val (page, _) = renderLandingPage(Fixture.timeSeriesObject)
			assert(sectionHeadings(page).contains("Production"))
			assert(propertyLink(page, "File made by") === RenderedLink("Test Person", "/resources/people/Test_Person"))
			assert(propertyLink(page, "Host organization") === RenderedLink(
				"Atmosphere Thematic Centre",
				"/resources/organizations/ATC"
			))
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
		}

		it("lists the spec documentation before the production documentation on data object landing pages") {
			val (page, _) = renderLandingPage(Fixture.timeSeriesObject)
			assert(propertyLinks(page, "Documentation") === Seq(
				RenderedLink("test_time_series_description.pdf", "/objects/ERERERERERERERERERERERER"),
				RenderedLink("Test document", "/objects/AgICAgICAgICAgICAgICAgIC")
			))
		}

		it("renders the version chain of a data object landing page") {
			val (page, counts) = renderLandingPage(Fixture.versionedObject)
			assert(counts === QueryCounts(connections = 1, statements = 63, existence = 0, sparql = 0))
			assert(propertyLink(page, "Previous version") === RenderedLink(
				"View previous version",
				"/objects/BAQEBAQEBAQEBAQEBAQEBAQE"
			))
			// the incomplete and the under-moratorium next versions are ignored; the remaining one lives in another graph
			assert(propertyLink(page, "Next version") === RenderedLink(
				"View next version",
				"/objects/BgYGBgYGBgYGBgYGBgYGBgYG"
			))
			// the latest version is reached through a plain collection that supersedes the next version
			val alert = deprecationAlert(page)
			assert(alert.heading === "Deprecated data")
			assert(alert.latestLinks === Seq(
				RenderedLink("CQkJCQkJCQkJCQkJCQkJCQkJ", "/objects/CQkJCQkJCQkJCQkJCQkJCQkJ"),
				RenderedLink("CgoKCgoKCgoKCgoKCgoKCgoK", "/objects/CgoKCgoKCgoKCgoKCgoKCgoK")
			))
			assert(alert.text.contains("Latest versions:"))
		}

		it("renders the acquisition site, instruments and sampling point of a data object landing page") {
			val (page, _) = renderLandingPage(Fixture.versionedObject)
			assert(metadataErrors(page) === Nil)
			assert(propertyText(section(page, "Acquisition"), "Location") === "TST tower area")
			assert(propertyText(mapCard(page), "Location") === "TST tower area")
			assert(propertyTexts(page, "Location").size === 2)
			assert(propertyLinks(page, "Ecosystem") === Seq(RenderedLink(
				"ENF - Evergreen Needleleaf Forests",
				"/resources/ecosystems/ENF"
			)))
			assert(propertyLinks(page, "Instrument").sortBy(_.href) === Seq(
				RenderedLink("Test instrument", "/resources/instruments/TST_1"),
				RenderedLink("Nafion dryer (SN-2)", "/resources/instruments/TST_2")
			))
			assert(propertyText(page, "Sampling height") === "25.0")
			assert(propertyText(page, "Sampling point") === "Tower inlet")
			assert(propertyText(page, "Coordinates") === "Lat: 56.1001, Lon: 13.4002")
		}

		it("lists only the current parent collections on data object landing pages") {
			val (page, _) = renderLandingPage(Fixture.timeSeriesObject)
			assert(propertyLinks(page, "Part of") === Seq(RenderedLink(
				"Test collection",
				"/collections/AwMDAwMDAwMDAwMDAwMDAwMD"
			)))
			val (nestedMember, _) = renderLandingPage(Fixture.versionedObject)
			assert(propertyLinks(nestedMember, "Part of") === Seq(RenderedLink(
				"Nested collection, version 2",
				"/collections/Dg4ODg4ODg4ODg4ODg4ODg4O"
			)))
		}

		it("renders the spatiotemporal data object landing page") {
			val (page, counts) = renderLandingPage(Fixture.spatialObject)
			assert(counts === QueryCounts(connections = 1, statements = 26, existence = 0, sparql = 0))
			assert(metadataErrors(page) === Nil)
			assert(heading(page) === "Test spatial data object")
			assert(propertyText(page, "Description") === "Gridded test data")
			assert(propertyText(page, "Temporal coverage from (UTC)") === "2020-01-01 00:00:00")
			assert(propertyText(page, "Temporal coverage to (UTC)") === "2020-12-31 00:00:00")
			assert(propertyText(page, "Temporal resolution") === "monthly")
			assert(propertyText(page, "Coverage") === "S: 50, W: 10, N: 60, E: 20")
			assert(propertyText(page, "Data level") === "3")
			assert(propertyLink(page, "File made by") === RenderedLink(
				"Atmosphere Thematic Centre",
				"/resources/organizations/ATC"
			))
			assert(sectionHeadings(page).contains("Acquisition") === false)

			// the regex-defined variable is listed under its actual name; the undefined one is left out
			val rows = tableRows(tableAfterHeading(page, "Previewable variables"))
			assert(rows.map(_.map(_.text)).sortBy(_.head) === Seq(
				Seq("flux_co2", "CO2 mixing ratio (dry mole fraction)", "µmol mol-1", "portion", "Preview"),
				Seq("tas", "air temperature", "K", "temperature", "Preview")
			))
		}

		it("returns the variable value ranges of the spatiotemporal data object as JSON") {
			val (json, counts) = renderJson(Fixture.spatialObject)
			assert(counts === QueryCounts(connections = 1, statements = 26, existence = 0, sparql = 0))
			val body = json.replaceAll("\\s", "")
			assert(body.contains(""""minMax":[250.5,310.25]"""), body)
			assert(!body.contains("unknown_var"))
		}
	}

	describe("document landing pages") {
		it("renders the document object landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.documentObject)
			assert(counts === QueryCounts(connections = 1, statements = 11, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.staticObject(Fixture.documentHash)))
			assert(heading(page) === "Test document")
			assert(propertyText(page, "File name") === "test_doc.pdf")
			assert(propertyText(page, "File size") === "53 KB (54321 bytes)")
			assert(propertyLink(page, "Submitted by") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))
			assert(sectionHeadings(page).contains("Submission"))
			assert(page.select("a[href='./AgICAgICAgICAgICAgICAgIC/test_doc.pdf.json']").size === 1)
		}

		it("renders the creators of the document object landing page as authors") {
			val (page, _) = renderLandingPage(Fixture.documentObject)
			assert(propertyLinks(page, "Authors") === Seq(
				RenderedLink("Zed Contributor", "/resources/people/Zed_Contributor"),
				RenderedLink("Test Person", "/resources/people/Test_Person")
			))
			// the document is part of the test collection, but not of its superseded previous version
			assert(propertyLinks(page, "Part of") === Seq(RenderedLink(
				"Test collection",
				"/collections/AwMDAwMDAwMDAwMDAwMDAwMD"
			)))
		}
	}

	describe("collection landing pages") {
		it("renders the collection landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.testCollection)
			assert(counts === QueryCounts(connections = 1, statements = 10, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.staticCollection(Fixture.testCollectionHash)))
			assert(heading(page) === "Test collection")
			assert(propertyText(page, "Description") === "A collection of test items")
			assert(propertyLink(page, "Collection creator") === RenderedLink(
				"Carbon Portal",
				"/resources/organizations/CP"
			))
			assert(propertyText(page, "Number of items") === "3")
			val itemLinks = collectionItems(page)
			assert(itemLinks.contains(RenderedLink(
				"test_data.csv",
				"https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB"
			)))
			assert(itemLinks.contains(RenderedLink(
				"Test document",
				"https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"
			)))
		}

		it("renders the nested collections and versions of the collection landing page") {
			val (page, _) = renderLandingPage(Fixture.testCollection)
			// members are sorted by name; the nested collection is listed by its title
			assert(collectionItems(page) === Seq(
				RenderedLink("Nested collection", "https://meta.icos-cp.eu/collections/DAwMDAwMDAwMDAwMDAwMDAwM"),
				RenderedLink("Test document", "https://meta.icos-cp.eu/objects/AgICAgICAgICAgICAgICAgIC"),
				RenderedLink("test_data.csv", "https://meta.icos-cp.eu/objects/AQEBAQEBAQEBAQEBAQEBAQEB")
			))
			assert(propertyLink(page, "Previous version") === RenderedLink(
				"View previous version",
				"/collections/DQ0NDQ0NDQ0NDQ0NDQ0NDQ0N"
			))
			assert(propertyLinks(page, "Next version").isEmpty)
			assert(propertyLinks(page, "Part of").isEmpty)
			assert(propertyLinks(page, "Documentation") === Seq(RenderedLink(
				"Test document",
				"/objects/AgICAgICAgICAgICAgICAgIC"
			)))
		}

		it("renders the nested collection landing page") {
			val (page, counts) = renderLandingPage(Fixture.nestedCollection)
			assert(counts === QueryCounts(connections = 1, statements = 10, existence = 0, sparql = 0))
			assert(heading(page) === "Nested collection")
			assert(propertyLinks(page, "Part of") === Seq(RenderedLink(
				"Test collection",
				"/collections/AwMDAwMDAwMDAwMDAwMDAwMD"
			)))
			assert(propertyLink(page, "Next version") === RenderedLink(
				"View next version",
				"/collections/Dg4ODg4ODg4ODg4ODg4ODg4O"
			))
			val alert = deprecationAlert(page)
			assert(alert.heading === "Deprecated collection")
			assert(alert.latestLinks === Seq(RenderedLink(
				"Dg4ODg4ODg4ODg4ODg4ODg4O",
				"/collections/Dg4ODg4ODg4ODg4ODg4ODg4O"
			)))
			assert(collectionItems(page) === Seq(RenderedLink(
				"versioned_data.csv",
				"https://meta.icos-cp.eu/objects/BQUFBQUFBQUFBQUFBQUFBQUF"
			)))
		}
	}

	describe("station landing pages") {
		it("renders the station landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.icosStation)
			assert(counts === QueryCounts(connections = 1, statements = 19, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.station(Fixture.icosStation)))
			assert(heading(page) === "Test station")
			assert(propertyText(page, "Station ID") === "TST")
			assert(propertyText(page, "Country code") === "SE")
			assert(propertyText(page, "Latitude/Longitude") === "56.1, 13.4")
			assert(propertyText(page, "Elevation") === "150 m")
		}

		it("renders the sub-resources of the ICOS station landing page") {
			val (page, _) = renderLandingPage(Fixture.icosStation)
			assert(metadataErrors(page) === Nil)
			assert(propertyText(page, "Description") === "Test station description")
			assert(propertyText(page, "WIGOS ID") === "0-20008-0-TST")
			assert(propertyText(page, "ICOS Station class") === "1")
			assert(propertyText(page, "ICOS Labeling date") === "2019-06-01")
			assert(propertyText(page, "Time zone offset") === "1")
			assert(propertyLinks(page, "Associated networks") === Seq(RenderedLink(
				"Test network",
				"/resources/networks/TestNet"
			)))
			assert(propertyLinks(page, "Documentation") === Seq(RenderedLink(
				"Test document",
				"/objects/AgICAgICAgICAgICAgICAgIC"
			)))
			assert(propertyLinks(page, "Organization") === Seq(RenderedLink(
				"Carbon Portal",
				"/resources/organizations/CP"
			)))

			// sorted by end date, the ongoing funding last
			val fundingRows = tableRows(tableAfterHeading(page, "Acknowledgements"))
			assert(fundingRows.map(_.map(_.text)) === Seq(
				Seq("Swedish Research Council", "2019-001", "Early award", "2019-01-01", "2020-12-31", ""),
				Seq("Swedish Research Council", "", "Ongoing award", "2021-01-01", "", "Ongoing funding")
			))
			val funder = RenderedLink("Swedish Research Council", "/resources/organizations/VR")
			val awardUrl = "https://example.org/awards/2019-001"
			assert(fundingRows.map(_.flatMap(links(_))) === Seq(
				Seq(funder, RenderedLink("2019-001", awardUrl), RenderedLink("Early award", awardUrl)),
				Seq(funder)
			))

			// the spatial coverage makes the location section with its map appear
			assert(sectionHeadings(page).contains("Location"))
			assert(page.select("iframe").eachAttr("src").asScala === Seq(
				"/station/?station=/resources/stations/TST&icon="
			))
			assert(page.select("img.img-fluid").eachAttr("src").asScala === Seq(
				"https://static.icos-cp.eu/images/stations/TST.jpg"
			))
		}

		it("renders the ecosystem station landing page with webpage elements") {
			val (page, counts) = renderLandingPage(Fixture.ecosystemStation)
			assert(counts === QueryCounts(connections = 1, statements = 9, existence = 0, sparql = 0))
			assert(metadataErrors(page) === Nil)
			assert(heading(page) === "ICOS STATION Test ecosystem station")
			assert(page.selectFirst(".wide-cover-image").attr("style").contains("https://static.icos-cp.eu/images/stations/ES_TST_cover.jpg"))
			assert(page.text.contains("Welcome to the test ecosystem station"))
			assert(links(page, "h3.h6 a") === Seq(
				RenderedLink("Station news", "https://example.org/es_tst/news"),
				RenderedLink("Station data", "https://example.org/es_tst/data")
			))
			assert(sectionHeadings(page).contains("Detailed information"))
			assert(propertyLinks(page, "Climate zone") === Seq(RenderedLink(
				"Dfc - Subarctic",
				"/resources/climateZones/Dfc"
			)))
			assert(propertyLinks(page, "Main ecosystem") === Seq(RenderedLink(
				"ENF - Evergreen Needleleaf Forests",
				"/resources/ecosystems/ENF"
			)))
			assert(propertyText(page, "Mean annual temperature") === "1.8 °C")
			assert(propertyText(page, "Mean annual precipitation") === "614.0 mm")
			assert(propertyText(page, "Mean annual incoming SW radiation") === "90.5 W/m2")
			assert(propertyLinks(page, "Documentation resource") === Seq(RenderedLink(
				"https://example.org/es_tst/docs",
				"https://example.org/es_tst/docs"
			)))
			assert(propertyLinks(page, "Data publication") === Seq(RenderedLink(
				"https://doi.org/10.1234/es_tst",
				"https://doi.org/10.1234/es_tst"
			)))
			assert(propertyText(page, "Latitude/Longitude") === "64.25, 19.77")
			assert(propertyText(page, "Elevation") === "235 m")
		}

		it("renders the SITES station landing page") {
			val (page, counts) = renderLandingPage(Fixture.sitesStation)
			assert(counts === QueryCounts(connections = 1, statements = 10, existence = 0, sparql = 0))
			assert(metadataErrors(page) === Nil)
			assert(heading(page) === "Testsjön Research Station")
			assert(propertyText(page, "Station ID") === "TSJ")
			assert(propertyLinks(page, "Main ecosystems") === Seq(
				RenderedLink("Forest", "/resources/ecosystems/forest"),
				RenderedLink("Lake", "/resources/ecosystems/lake")
			))
			assert(propertyLinks(page, "Climate zone") === Seq(RenderedLink(
				"Dfb - Warm-summer humid continental",
				"/resources/climateZones/Dfb"
			)))
			assert(propertyText(page, "Mean annual temperature") === "5.5 °C")
			assert(propertyText(page, "Operational period") === "2015-")
			assert(propertyLinks(page, "Documentation") === Seq(RenderedLink(
				"testsjon_description.pdf",
				"/objects/EBAQEBAQEBAQEBAQEBAQEBAQ"
			)))
		}

		it("returns the sites of the SITES station, with their ecosystems and coverages, as JSON") {
			val (body, counts) = renderJson(Fixture.sitesStation)
			assert(counts === QueryCounts(connections = 1, statements = 10, existence = 0, sparql = 0))
			Seq("Testsjön forest", "Forest mast", "Testsjön lake", "Lake outline", "Polygon").foreach { expected =>
				assert(body.contains(expected), s"'$expected' missing in $body")
			}
		}
	}

	describe("organization landing pages") {
		it("renders the organization landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.organization)
			assert(counts === QueryCounts(connections = 1, statements = 2, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.organization(Fixture.organization)))
			assert(heading(page) === "Carbon Portal (CP)")
			assert(propertyText(page, "Name") === "Carbon Portal")
		}
	}

	describe("instrument landing pages") {
		it("renders the instrument landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.instrument)
			assert(counts === QueryCounts(connections = 1, statements = 9, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.instrument(Fixture.instrument)))
			assert(heading(page) === "Test instrument")
			assert(propertyText(page, "Model") === "Picarro G2401")
			assert(propertyText(page, "Serial number") === "SN-1")
			assert(propertyLink(page, "Owner") === RenderedLink("Carbon Portal", "/resources/organizations/CP"))
		}

		it("renders the vendor, components and deployments of the instrument landing page") {
			val (page, _) = renderLandingPage(Fixture.instrument)
			assert(metadataErrors(page) === Nil)
			assert(propertyLink(page, "Vendor") === RenderedLink("Picarro Inc.", "/resources/organizations/Picarro"))
			assert(propertyLinks(page, "Has component") === Seq(RenderedLink(
				"Nafion dryer (SN-2)",
				"/resources/instruments/TST_2"
			)))
			assert(propertyLinks(page, "Is part of").isEmpty)
			assert(propertyText(page, "Comment") === "Main analyser")
			// all deployments are listed, regardless of any acquisition interval
			val deploymentRows = tableRows(tableAfterHeading(page, "Deployments"))
			assert(deploymentRows.map(_.map(_.text)).sortBy(_(6)) === Seq(
				Seq("co2", "CO2 column", "Test station", "", "", "", "2018-01-01 00:00:00", "2019-01-01 00:00:00"),
				Seq("co2", "CO2 column", "Test station", "56.1", "13.4", "50.0 m", "2020-06-01 00:00:00", "")
			))
			assert(deploymentRows.head.flatMap(links(_)) === Seq(
				RenderedLink("CO2 column", "/resources/cpmeta/testTimeSeriesDataset_co2"),
				RenderedLink("Test station", "/resources/stations/TST")
			))
		}

		it("renders the instrument component landing page") {
			val (page, counts) = renderLandingPage(Fixture.instrumentComponent)
			assert(counts === QueryCounts(connections = 1, statements = 3, existence = 0, sparql = 0))
			assert(heading(page) === "Nafion dryer (SN-2)")
			assert(propertyLinks(page, "Is part of") === Seq(RenderedLink(
				"Test instrument",
				"/resources/instruments/TST_1"
			)))
			assert(sectionHeadings(page).contains("Deployments") === false)
		}
	}

	describe("person landing pages") {
		it("renders the person landing page as HTML") {
			val (page, counts) = renderLandingPage(Fixture.person)
			assert(counts === QueryCounts(connections = 1, statements = 4, existence = 0, sparql = 0))
			assert(counts === loadLandingPage(_.person(Fixture.person)))
			assert(heading(page) === "Test Person")
			assert(propertyText(page, "First name") === "Test")
			assert(propertyText(page, "Last name") === "Person")
			assert(page.select("table tbody tr td").asScala.map(_.text) === Seq("PI", "TST", "2021-01-01", ""))
		}
	}

	/**
	 * There is no Twirl page for an object specification, nor for a labeled resource. Both are
	 * recognized by the serializer, but only so that their JSON can be served by the loader's
	 * dedicated readers; in HTML both fall through to the generic resource page, which is why
	 * neither test below reads anything the loader built for that resource kind.
	 */
	describe("URIs recognized by the serializer but rendered by the generic page") {
		it("renders an object specification as the generic resource page") {
			val (page, counts) = renderLandingPage(Fixture.objectSpec)
			assert(counts === QueryCounts(connections = 2, statements = 0, existence = 1, sparql = 2))
			assert(counts === probeCounts(Fixture.objectSpec) + loadGenericResource(Fixture.objectSpec))
			assert(heading(page) === "Test time series")
			assert(propertyText(page, "Label") === "Test time series")
			//the properties are labeled by the ontology predicates, as on any generic page, rather
			//than by the names a specification landing page would give them
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasAssociatedProject") === RenderedLink(
				"ICOS",
				"/resources/projects/icos"
			))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasDataTheme") === RenderedLink(
				"Atmosphere",
				"/resources/themes/atmosphere"
			))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasFormat") === RenderedLink(
				"ASCII CSV time series",
				"/ontologies/cpmeta/csvWithIso8601tsFirstCol"
			))
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasEncoding") === RenderedLink(
				"plain text",
				"/ontologies/cpmeta/asciiEncoding"
			))
			assert(propertyWithLinkedLabel(page, "/ontologies/cpmeta/hasDataLevel").text === "2")
			assert(linkedLabelProperty(page, "/ontologies/cpmeta/hasDocumentationObject") ===
				RenderedLink(
					"https://meta.icos-cp.eu/objects/ERERERERERERERERERERERER",
					"/objects/ERERERERERERERERERERERER"
				))
		}

		it("serves the object specification as JSON") {
			val (body, counts) = renderJson(Fixture.objectSpec)
			assert(counts === QueryCounts(connections = 2, statements = 7, existence = 1, sparql = 0))
			assert(counts === probeCounts(Fixture.objectSpec) + loadLandingPage(_.specification(Fixture.objectSpec)))
			assert(body.contains("Test time series"))
			assert(body.contains("Atmosphere"))
		}

		it("renders a labeled resource as the generic resource page") {
			val (page, counts) = renderLandingPage(Fixture.dataTheme)
			assert(counts === QueryCounts(connections = 3, statements = 0, existence = 2, sparql = 2))
			assert(counts === probeCounts(Fixture.dataTheme) + loadGenericResource(Fixture.dataTheme))
			assert(heading(page) === "Atmosphere")
			assert(propertyText(page, "Label") === "Atmosphere")
			assert(
				propertyWithLinkedLabel(page, "/ontologies/cpmeta/hasIcon").text === "https://static.icos-cp.eu/atmosphere.svg"
			)
		}

		it("serves the labeled resource as JSON") {
			val (body, counts) = renderJson(Fixture.dataTheme)
			assert(counts === QueryCounts(connections = 3, statements = 1, existence = 2, sparql = 0))
			assert(counts === probeCounts(Fixture.dataTheme) + loadLandingPage(_.labeledResource(Fixture.dataTheme)))
			assert(body.contains("Atmosphere"))
		}
	}


	private val config = TestConfig.metaConfig
	private given Envri = Envri.ICOS
	private given EnvriConfigs = config.core.envriConfigs
	private val lenses = MetaDb.getLenses(config.instanceServers, config.dataUploadService)

	/** Serves the URI from a fresh counting view of the fixture, so the counts cover this route only. */
	private def serialize(uri: Uri): (Route, () => QueryCounts) = {
		val countingRepo = CountingRepository(repo)
		val serializer = new Rdf4jUriSerializer(
			countingRepo,
			CpVocab(countingRepo.getValueFactory),
			CpmetaVocab(countingRepo.getValueFactory),
			lenses,
			DerivedMetadataClient(config.remoteRdfRepository.get.derivedMetadataEndpoint),
			config
		)

		given ToResponseMarshaller[Uri] = serializer.marshaller
		get(complete(uri)) -> (() => countingRepo.counts)
	}

	/** The new read path, reading the fixture through a fresh counting view of it. */
	private def loader(): (LandingPageLoader, CountingRepository) = {
		val countingRepo = CountingRepository(repo)
		val vocab = CpVocab(countingRepo.getValueFactory)
		val metaVocab = CpmetaVocab(countingRepo.getValueFactory)
		val pidFactory = HandleNetClient.PidFactory(config.dataUploadService.handle)
		val attribution = AttributionProvider(vocab, metaVocab)
		val loader = LandingPageLoader(countingRepo, vocab, metaVocab, lenses, pidFactory, attribution)
		loader -> countingRepo
	}

	/**
	 * Builds one page with the loader, requiring it to have been built without errors, and returns
	 * the query counts of that build.
	 */
	private def loadLandingPage(page: LandingPageLoader => Validated[?]): QueryCounts = {
		val (pageLoader, repo) = loader()
		val built = page(pageLoader)
		assert(built.errors === Nil)
		assert(built.result.isDefined, "the page was not built at all")
		repo.counts
	}

	private def loadGenericResource(uri: Uri): QueryCounts = {
		val (pageLoader, repo) = loader()
		val built = pageLoader.genericResource(uri)
		assert(built.isSuccess, built)
		repo.counts
	}

	/**
	 * What the serializer's routing spends before it even knows which page a `/resources/...` URI
	 * gets: an existence check for a data level, and, when that misses, one for a label. Pages
	 * reached by URI shape alone (objects, collections, stations, ...) pay none of this.
	 */
	private def probeCounts(uri: Uri): QueryCounts = {
		val (pageLoader, repo) = loader()
		if (!pageLoader.isObjectSpecification(uri)) pageLoader.isLabeledResource(uri)
		repo.counts
	}

	/** Renders the page as HTML. */
	private def renderLandingPage(uri: Uri): (Document, QueryCounts) = {
		val (route, getCounts) = serialize(uri)
		val page = Get() ~> Accept(MediaTypes.`text/html`) ~> route ~> check {
			val body = responseAs[String]
			assert(status === StatusCodes.OK, body)
			assert(contentType.mediaType === MediaTypes.`text/html`)
			Jsoup.parse(body)
		}
		page -> getCounts()
	}

	/** Renders the statements as RDF. */
	private def renderRdf(uri: Uri, accepted: MediaType, returned: ContentType): (String, QueryCounts) = {
		val (route, getCounts) = serialize(uri)
		val body = Get() ~> Accept(accepted) ~> route ~> check {
			val body = responseAs[String]
			assert(status === StatusCodes.OK, body)
			assert(contentType === returned)
			body
		}
		body -> getCounts()
	}

	/** Renders the JSON representation. */
	private def renderJson(uri: Uri): (String, QueryCounts) = {
		val (route, getCounts) = serialize(uri)
		val body = Get() ~> Accept(MediaTypes.`application/json`) ~> route ~> check {
			val body = responseAs[String]
			assert(status === StatusCodes.OK, body)
			assert(contentType === ContentTypes.`application/json`)
			body
		}
		body -> getCounts()
	}


	private case class RenderedLink(text: String, href: String)
	private object RenderedLink {
		def apply(elem: Element): RenderedLink = RenderedLink(elem.text, elem.attr("href"))
	}

	private def links(scope: Element, selector: String = "a"): Seq[RenderedLink] =
		scope.select(selector).asScala.map(RenderedLink(_)).toSeq

	private def heading(page: Document): String =
		Option(page.selectFirst("h1")).map(_.text).getOrElse(fail("Missing heading"))

	/** The properties listed under a main-column section heading, up to the next heading. */
	private def section(page: Document, heading: String): Element = {
		val h2 = page.select("div.col-md-8 > div.row > h2").asScala.find(_.text == heading)
			.getOrElse(fail(s"Missing section '$heading'"))
		val scope = Element("div")
		Iterator.iterate(h2.nextElementSibling)(_.nextElementSibling)
			.takeWhile(elem => elem != null && elem.tagName != "h2")
			.foreach(elem => scope.appendChild(elem.clone()))
		scope
	}

	/** The side-column card below the location map. */
	private def mapCard(page: Document): Element =
		Option(page.selectFirst("div.col-md-4 .card:has(iframe)")).getOrElse(fail("Missing map card"))

	private def propertyValues(scope: Element, label: String): Seq[Element] =
		scope.select("label.fw-bold").asScala.filter(_.text == label).map(_.parent.nextElementSibling).toSeq

	private def property(scope: Element, label: String): Element =
		propertyValues(scope, label) match {
			case Seq(single) => single
			case Seq() => fail(s"Missing property '$label'")
			case many => fail(s"Property '$label' appears ${many.size} times")
		}

	private def propertyWithLinkedLabel(scope: Element, href: String): Element =
		scope.select("label.fw-bold a").asScala.filter(_.attr("href") == href).toSeq match {
			case Seq(single) => single.parent.parent.nextElementSibling
			case Seq() =>
				fail(s"Missing property with link '$href'; found ${scope.select("label.fw-bold a").eachAttr("href")}")
			case many => fail(s"Property with link '$href' appears ${many.size} times")
		}

	private def propertyLinks(scope: Element, label: String): Seq[RenderedLink] =
		propertyValues(scope, label).flatMap(links(_))

	private def propertyText(scope: Element, label: String): String = property(scope, label).text

	private def propertyTexts(scope: Element, label: String): Seq[String] = propertyValues(scope, label).map(_.text)

	private def singleLink(value: Element, description: String): RenderedLink =
		links(value) match {
			case Seq(single) => single
			case links => fail(s"Expected one link for $description, got $links")
		}

	private def propertyLink(scope: Element, label: String): RenderedLink =
		singleLink(property(scope, label), s"'$label'")

	private def linkedLabelProperty(scope: Element, labelHref: String): RenderedLink =
		singleLink(propertyWithLinkedLabel(scope, labelHref), s"'$labelHref'")

	private def tableAfterHeading(page: Document, heading: String): Element =
		page.select("h2").asScala.find(_.text == heading)
			.flatMap(h2 =>
				Option(h2.nextElementSibling).map(sibling =>
					if (sibling.tagName == "table") sibling else sibling.selectFirst("table")
				)
			)
			.getOrElse(fail(s"Missing table after heading '$heading'"))

	private def tableRows(table: Element): Seq[Seq[Element]] =
		table.selectFirst("tbody").children.asScala.map(_.children.asScala.toSeq).toSeq

	private def metadataErrors(page: Document): Seq[String] =
		page.select("div.alert.d-flex > div").asScala.map(_.text).toSeq

	private def sectionHeadings(page: Document): Seq[String] =
		page.select("h2").asScala.map(_.text).toSeq

	private def collectionItems(page: Document): Seq[RenderedLink] = links(page, "a[target=_blank]")

	private case class DeprecationAlert(heading: String, text: String, latestLinks: Seq[RenderedLink])

	private def deprecationAlert(page: Document): DeprecationAlert = {
		val alert = Option(page.selectFirst(".alert-warning")).getOrElse(fail("Missing deprecation alert"))
		DeprecationAlert(
			alert.selectFirst(".alert-heading").text,
			alert.text,
			links(alert, "a.alert-link")
		)
	}
}
