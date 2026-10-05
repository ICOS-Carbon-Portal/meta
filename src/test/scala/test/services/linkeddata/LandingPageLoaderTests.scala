 package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.data.{
	AtcStationSpecifics,
	DataObject,
	DatasetType,
	DocObject,
	EnvriConfigs,
	Position,
	StaticObject,
	TimeInterval,
	UriResource
}
import se.lu.nateko.cp.meta.services.citation.{CitationMaker, CitationStyle, PlainDoiCiter}
import se.lu.nateko.cp.meta.services.linkeddata.LandingPageLoader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.test.TestConfig

import java.net.URI
import java.time.Instant

/**
 * Guards what the loader reads, and the number of RDF-store round trips it takes to read it.
 *
 * The loader is given an in-memory triplestore with a hand-made, minimal-but-complete metadata
 * fixture, wrapped in a proxy that counts every statement lookup, existence check and SPARQL
 * query made through it. Each expectation below is therefore a snapshot of how chatty one read
 * is: a change in the counts means the read path changed, and the new number has to be looked at
 * (and only then written down here) rather than silently accepted.
 *
 * Most of what the loader reads becomes a landing page of its own. The specification and the
 * labeled resource do not: they have no Twirl page, and the serializer serves them as JSON only,
 * falling back to the generic resource page for HTML.
 */
class LandingPageLoaderTests extends AnyFunSpec with BeforeAndAfterAll {
	private val repo = Fixture.createRepo()

	private given Envri = Envri.ICOS
	private val config = TestConfig.metaConfig
	private given EnvriConfigs = config.core.envriConfigs
	private val metaVocab = CpmetaVocab(repo.getValueFactory)
	private val vocab = CpVocab(repo.getValueFactory)

	private val lenses = MetaDb.getLenses(config.instanceServers, config.dataUploadService)
	private val pidFactory = HandleNetClient.PidFactory(config.dataUploadService.handle)
	private val citer = {
		val doiCiter = new PlainDoiCiter {
			def getCitationEager(doi: se.lu.nateko.cp.doi.Doi, style: CitationStyle) = None
			def getDoiEager(doi: se.lu.nateko.cp.doi.Doi) = None
		}
		CitationMaker(doiCiter, vocab, metaVocab, config.core)
	}

	private def build[T](read: LandingPageLoader => T): (T, QueryCounts) = {
		val countingRepo = CountingRepository(repo)
		val loader = LandingPageLoader(countingRepo, vocab, metaVocab, lenses, pidFactory, citer)
		val result = read(loader)
		result -> countingRepo.counts
	}

	private def buildValidated[T](page: LandingPageLoader => Validated[T]): (T, QueryCounts) = {
		val (built, counts) = build(page)
		assert(built.errors === Nil)
		built.result.getOrElse(fail("the page was not built at all")) -> counts
	}

	override def afterAll(): Unit = {
		repo.shutDown()
	}

	describe("data object landing page") {
		lazy val (page, counts) = buildValidated(_.staticObject(Fixture.timeSeriesHash))

		it("reads the object with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 310, existence = 11, sparql = 0))
		}

		it("has the file-level metadata of the object") {
			val dobj = asDataObject(page)
			assert(dobj.hash === Fixture.timeSeriesHash)
			assert(dobj.fileName === "test_data.csv")
			assert(dobj.size === Some(12345L))
			assert(dobj.accessUrl === Some(vocab.getStaticObjectAccessUrl(Fixture.timeSeriesHash)))
			assert(dobj.pid === Some(s"11676/${Fixture.timeSeriesHash.id}"))
			assert(dobj.doi === None)
			assert(dobj.submission.submitter.name === "Carbon Portal")
			assert(dobj.submission.start === Fixture.submissionStart)
			assert(dobj.submission.stop === Some(Fixture.submissionStop))
		}

		it("has the specification of the object") {
			val spec = asDataObject(page).specification
			assert(spec.self.uri === URI(Fixture.objectSpec.toString))
			assert(spec.self.label === Some("Test time series"))
			assert(spec.dataLevel === 2)
			assert(spec.specificDatasetType === DatasetType.StationTimeSeries)
			assert(spec.project.self.label === Some("ICOS"))
			assert(spec.theme.self.label === Some("Atmosphere"))
			assert(spec.format.self.label === Some("ASCII CSV time series"))
			assert(spec.encoding.label === Some("plain text"))
		}

		it("has the station time series acquisition metadata") {
			val l2 = asDataObject(page).specificInfo match {
				case Right(stationTimeSeries) => stationTimeSeries
				case Left(spatioTemporal) => fail(s"Expected station time series metadata, got $spatioTemporal")
			}
			assert(l2.nRows === Some(100))
			assert(l2.columns.map(_.map(_.label)) === Some(Seq("TIMESTAMP", "co2", "ch4")))
			// only the co2 deployment that overlaps the acquisition interval is kept
			assert(l2.columns.map(_.map(_.instrumentDeployments.map(_.map(_.instrument.uri)))) ===
				Some(Seq(None, Some(Seq(URI(Fixture.instrument.toString))), None)))
			val production = l2.productionInfo.getOrElse(fail("Missing production"))
			assert(production.comment === Some("Test production comment"))
			assert(production.host.map(_.name) === Some("Atmosphere Thematic Centre"))
			assert(production.contributors.size === 3)
			assert(production.sources.size === 2)
			assert(production.dateTime === Instant.parse("2022-01-01T12:00:00Z"))
			assert(l2.acquisition.station.id === "TST")
			assert(l2.acquisition.station.org.name === "Test station")
			assert(l2.acquisition.interval === Some(TimeInterval(Fixture.acquisitionStart, Fixture.acquisitionStop)))
			assert(l2.acquisition.samplingHeight === Some(50f))
			assert(l2.acquisition.instruments.map(_.label) === Seq(Some("Test instrument")))
		}

		it("knows the collection the object is a part of, and that it is the only version") {
			val dobj = asDataObject(page)
			assert(dobj.parentCollections.map(_.label) === Seq(Some("Test collection")))
			assert(dobj.previousVersion === None)
			assert(dobj.nextVersion === None)
			assert(dobj.latestVersion === Left(URI(Fixture.timeSeriesObject.toString)))
		}
	}

	describe("document object landing page") {
		lazy val (page, counts) = buildValidated(_.staticObject(Fixture.documentHash))

		it("reads the document with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 42, existence = 2, sparql = 0))
		}

		it("is built into a document object with its title and authors") {
			val doc = asDocObject(page)
			assert(doc.hash === Fixture.documentHash)
			assert(doc.fileName === "test_doc.pdf")
			assert(doc.size === Some(54321L))
			assert(doc.accessUrl === Some(vocab.getStaticObjectAccessUrl(Fixture.documentHash)))
			assert(doc.pid === Some(s"11676/${Fixture.documentHash.id}"))
			assert(doc.description === None)
			assert(doc.references.title === Some("Test document"))
			assert(doc.references.authors.map(_.map(_.self.label)) === Some(Seq(
				Some("Zed Contributor"),
				Some("Test Person")
			)))
			assert(doc.submission.submitter.name === "Carbon Portal")
			assert(doc.parentCollections.map(_.label) === Seq(Some("Test collection")))
		}
	}

	describe("collection landing page") {
		lazy val (page, counts) = buildValidated(_.staticCollection(Fixture.testCollectionHash))

		it("reads the collection with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 26, existence = 4, sparql = 0))
		}

		it("is built into a collection with all of its members") {
			assert(page.res === URI(Fixture.testCollection.toString))
			assert(page.hash === Fixture.testCollectionHash)
			assert(page.title === "Test collection")
			assert(page.description === Some("A collection of test items"))
			assert(page.creator.name === "Carbon Portal")
			assert(page.doi === None)
			assert(page.members.map(_.res).toSet === Set(
				URI(Fixture.nestedCollection.toString),
				URI(Fixture.timeSeriesObject.toString),
				URI(Fixture.documentObject.toString)
			))
			// members are sorted by name, and a document object or a collection is named by its title
			assert(page.members.map(_.name) === Seq("Nested collection", "Test document", "test_data.csv"))
			assert(page.parentCollections === Nil)
		}
	}

	describe("station landing page") {
		lazy val (page, counts) = buildValidated(_.station(Fixture.icosStation))

		it("reads the station and its memberships with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 96, existence = 4, sparql = 0))
		}

		it("is built into a station with its location and country") {
			val station = page.org
			assert(station.id === "TST")
			assert(station.org.self.uri === URI(Fixture.icosStation.toString))
			assert(station.org.name === "Test station")
			assert(station.location === Some(Position(56.1, 13.4, Some(150f), Some("TST"), None)))
			assert(station.countryCode.map(_.code) === Some("SE"))
			assert(station.responsibleOrganization.map(_.name) === Some("Carbon Portal"))
			assert(station.specificInfo.isInstanceOf[AtcStationSpecifics])
		}

		it("is built with the station's staff") {
			assert(page.staff.map(_.person.self.label) === Seq(Some("Test Person")))
			assert(page.staff.map(_.role.role.label) === Seq(Some("PI")))
			assert(page.staff.map(_.role.start) === Seq(Some(Fixture.acquisitionStart)))
			assert(page.currentStaff.size === 1)
			assert(page.formerStaff === Nil)
		}
	}

	describe("organization landing page") {
		lazy val (page, counts) = buildValidated(_.organization(Fixture.organization))

		it("reads the organization and its memberships with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 7, existence = 0, sparql = 0))
		}

		it("is built into an organization without staff of its own") {
			assert(page.org.self.label === Some("CP"))
			assert(page.org.name === "Carbon Portal")
			assert(page.org.email === None)
			assert(page.org.website === None)
			// the only membership in the fixture is at the station, not at this organization
			assert(page.staff === Nil)
		}
	}

	describe("person landing page") {
		lazy val (page, counts) = buildValidated(_.person(Fixture.person))

		it("reads the person and their roles with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 17, existence = 0, sparql = 0))
		}

		it("is built into a person with their role at the station") {
			assert(page.person.self.uri === URI(Fixture.person.toString))
			assert(page.person.firstName === "Test")
			assert(page.person.lastName === "Person")
			assert(page.person.orcid === None)
			assert(page.roles.map(_.org.label) === Seq(Some("TST")))
			assert(page.roles.map(_.role.role.label) === Seq(Some("PI")))
		}
	}

	describe("instrument landing page") {
		lazy val (page, counts) = buildValidated(_.instrument(Fixture.instrument))

		it("reads the instrument with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 66, existence = 1, sparql = 0))
		}

		it("is built into an instrument with its model, serial number, owner, vendor, parts and deployments") {
			assert(page.self.uri === URI(Fixture.instrument.toString))
			assert(page.self.label === Some("Test instrument"))
			assert(page.model === "Picarro G2401")
			assert(page.serialNumber === "SN-1")
			assert(page.name === Some("Test instrument"))
			assert(page.owner.map(_.name) === Some("Carbon Portal"))
			assert(page.vendor.map(_.name) === Some("Picarro Inc."))
			assert(page.parts.map(_.uri) === Seq(URI(Fixture.instrumentComponent.toString)))
			assert(page.partOf === None)
			assert(page.deployments.map(_.start).toSet === Set(
				Some(Instant.parse("2020-06-01T00:00:00Z")),
				Some(Instant.parse("2018-01-01T00:00:00Z"))
			))
		}
	}

	describe("object specification metadata") {
		lazy val (page, counts) = buildValidated(_.specification(Fixture.objectSpec))

		it("reads the specification with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 29, existence = 0, sparql = 0))
		}

		it("is built into a specification with project, theme, format and encoding") {
			assert(page.self.uri === URI(Fixture.objectSpec.toString))
			assert(page.self.label === Some("Test time series"))
			assert(page.dataLevel === 2)
			assert(page.specificDatasetType === DatasetType.StationTimeSeries)
			assert(page.project.self.label === Some("ICOS"))
			assert(page.theme.self.uri === URI(Fixture.dataTheme.toString))
			assert(page.theme.icon === URI("https://static.icos-cp.eu/atmosphere.svg"))
			assert(page.format.self.label === Some("ASCII CSV time series"))
			assert(page.encoding.label === Some("plain text"))
			assert(page.datasetSpec.map(_.self.label) === Some(Some("Test time series dataset")))
			assert(page.documentation.map(_.res) === Seq(URI(Fixture.specDocumentObject.toString)))
			assert(page.keywords === None)
		}

		it("recognizes the specification with a single existence check") {
			val (recognized, counts) = build(_.isObjectSpecification(Fixture.objectSpec))
			assert(recognized)
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))
		}
	}

	describe("labeled resource metadata") {
		lazy val (page, counts) = buildValidated(_.labeledResource(Fixture.dataTheme))

		it("reads the labeled resource with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 2, existence = 0, sparql = 0))
		}

		it("is built into the URI, label and comments of the resource") {
			assert(page === UriResource(URI(Fixture.dataTheme.toString), Some("Atmosphere"), Nil))
		}

		it("recognizes the labeled resource with a single existence check") {
			val (recognized, counts) = build(_.isLabeledResource(Fixture.dataTheme))
			assert(recognized)
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))
		}
	}

	describe("generic (fallback) resource page") {
		lazy val (page, counts) = build(_.genericResource(Fixture.icosStation).get)

		it("is served by exactly two SPARQL queries") {
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 2))
		}

		it("is built into the properties, types and usages of the resource") {
			assert(!page.isEmpty)
			assert(page.res === UriResource(
				URI(Fixture.icosStation.toString),
				Some("TST"),
				Seq("Test station description")
			))
			assert(page.types.map(_.uri) === List(URI(metaVocab.atmoStationClass.stringValue)))

			val props = page.propValues.map((prop, value) => prop.uri.toString -> value)
			assert(props.contains(metaVocab.hasStationId.stringValue -> Right("TST")))
			assert(props.contains(metaVocab.hasName.stringValue -> Right("Test station")))

			// the acquisition of the data object, and the membership of the person, point at the station
			val usages = page.usage.map((subj, prop) => subj.uri.toString -> prop.uri.toString)
			assert(usages.contains(
				s"http://meta.icos-cp.eu/resources/acq_${Fixture.timeSeriesHash.id}" ->
					metaVocab.prov.wasAssociatedWith.stringValue
			))
			assert(usages.exists((_, prop) => prop == metaVocab.atOrganization.stringValue))
		}
	}

	private def asDataObject(obj: StaticObject): DataObject = obj match {
		case dobj: DataObject => dobj
		case other => fail(s"Expected a DataObject, got $other")
	}

	private def asDocObject(obj: StaticObject): DocObject = obj match {
		case doc: DocObject => doc
		case other => fail(s"Expected a DocObject, got $other")
	}
}
