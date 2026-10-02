package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{HandleNetClient, UriId}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{
	AtcStationSpecifics, DataObject, DatasetType, DocObject, EnvriConfig, EnvriConfigs, Position,
	StaticObject, TimeInterval, UriResource
}
import se.lu.nateko.cp.meta.services.citation.{CitationMaker, CitationStyle, PlainDoiCiter}
import se.lu.nateko.cp.meta.services.linkeddata.LandingPageLoader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}

import java.net.URI
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.Using

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

	import LandingPageLoaderTests.{*, given}

	private given system: ActorSystem = ActorSystem("LandingPageLoaderTests")
	private given Materializer = Materializer.matFromSystem
	private given ExecutionContext = system.dispatcher

	private val fixture = Fixture()
	private val doiCiter = new PlainDoiCiter {
		def getCitationEager(doi: se.lu.nateko.cp.doi.Doi, style: CitationStyle) = None
		def getDoiEager(doi: se.lu.nateko.cp.doi.Doi) = None
	}
	private val citer = CitationMaker(doiCiter, fixture.vocab, fixture.metaVocab, config.core)
	private val lenses = MetaDb.getLenses(config.instanceServers, config.dataUploadService)
	private val pidFactory = HandleNetClient.PidFactory(config.dataUploadService.handle)

	/** Makes one read with a loader of its own, over a fresh counting view of the fixture. */
	private def counted[T](read: LandingPageLoader => T): (T, QueryCounts) = {
		val repo = CountingRepository(fixture.repo)
		val loader = LandingPageLoader(repo, fixture.vocab, fixture.metaVocab, lenses, pidFactory, citer)
		val result = read(loader)
		result -> repo.counts
	}

	override def afterAll(): Unit = {
		fixture.repo.shutDown()
		system.terminate()
	}

	/**
	 * Builds one page, requiring it to have been built without errors, and captures the query
	 * counts of that build. Called from a `lazy val` so that the tests sharing the page build it
	 * only once.
	 */
	private def build[T](page: LandingPageLoader => Validated[T]): (T, QueryCounts) = {
		val (built, counts) = counted(page)
		assert(built.errors === Nil)
		built.result.getOrElse(fail("the page was not built at all")) -> counts
	}

	private def asDataObject(obj: StaticObject): DataObject = obj match {
		case dobj: DataObject => dobj
		case other => fail(s"Expected a DataObject, got $other")
	}

	private def asDocObject(obj: StaticObject): DocObject = obj match {
		case doc: DocObject => doc
		case other => fail(s"Expected a DocObject, got $other")
	}

	describe("data object landing page") {
		lazy val (page, counts) = build(_.staticObject(fixture.dobjHash))

		it("reads the object with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 310, existence = 11, sparql = 0))
		}

		it("has the file-level metadata of the object") {
			val dobj = asDataObject(page)
			assert(dobj.hash === fixture.dobjHash)
			assert(dobj.fileName === "test_data.csv")
			assert(dobj.size === Some(12345L))
			assert(dobj.accessUrl === Some(fixture.dobjAccessUrl))
			assert(dobj.pid === Some(s"11676/${fixture.dobjHash.id}"))
			assert(dobj.doi === None)
			assert(dobj.submission.submitter.name === "Carbon Portal")
			assert(dobj.submission.start === fixture.submStart)
			assert(dobj.submission.stop === Some(fixture.submStop))
		}

		it("has the specification of the object") {
			val spec = asDataObject(page).specification
			assert(spec.self.uri === fixture.specResource)
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
			//only the co2 deployment that overlaps the acquisition interval is kept
			assert(l2.columns.map(_.map(_.instrumentDeployments.map(_.map(_.instrument.uri)))) ===
				Some(Seq(None, Some(Seq(fixture.instrumentResource)), None)))
			val production = l2.productionInfo.getOrElse(fail("Missing production"))
			assert(production.comment === Some("Test production comment"))
			assert(production.host.map(_.name) === Some("Atmosphere Thematic Centre"))
			assert(production.contributors.size === 3)
			assert(production.sources.size === 2)
			assert(production.dateTime === Instant.parse("2022-01-01T12:00:00Z"))
			assert(l2.acquisition.station.id === "TST")
			assert(l2.acquisition.station.org.name === "Test station")
			assert(l2.acquisition.interval === Some(TimeInterval(fixture.acqStart, fixture.acqStop)))
			assert(l2.acquisition.samplingHeight === Some(50f))
			assert(l2.acquisition.instruments.map(_.label) === Seq(Some("Test instrument")))
		}

		it("knows the collection the object is a part of, and that it is the only version") {
			val dobj = asDataObject(page)
			assert(dobj.parentCollections.map(_.label) === Seq(Some("Test collection")))
			assert(dobj.previousVersion === None)
			assert(dobj.nextVersion === None)
			assert(dobj.latestVersion === Left(fixture.dobjResource))
		}
	}

	describe("document object landing page") {
		lazy val (page, counts) = build(_.staticObject(fixture.docHash))

		it("reads the document with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 42, existence = 2, sparql = 0))
		}

		it("is built into a document object with its title and authors") {
			val doc = asDocObject(page)
			assert(doc.hash === fixture.docHash)
			assert(doc.fileName === "test_doc.pdf")
			assert(doc.size === Some(54321L))
			assert(doc.accessUrl === Some(fixture.docAccessUrl))
			assert(doc.pid === Some(s"11676/${fixture.docHash.id}"))
			assert(doc.description === None)
			assert(doc.references.title === Some("Test document"))
			assert(doc.references.authors.map(_.map(_.self.label)) === Some(Seq(Some("Zed Contributor"), Some("Test Person"))))
			assert(doc.submission.submitter.name === "Carbon Portal")
			assert(doc.parentCollections.map(_.label) === Seq(Some("Test collection")))
		}
	}

	describe("collection landing page") {
		lazy val (page, counts) = build(_.staticCollection(fixture.collHash))

		it("reads the collection with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 26, existence = 4, sparql = 0))
		}

		it("is built into a collection with all of its members") {
			assert(page.res === fixture.collResource)
			assert(page.hash === fixture.collHash)
			assert(page.title === "Test collection")
			assert(page.description === Some("A collection of test items"))
			assert(page.creator.name === "Carbon Portal")
			assert(page.doi === None)
			assert(page.members.map(_.res).toSet === Set(fixture.nestedCollResource, fixture.dobjResource, fixture.docResource))
			//members are sorted by name, and a document object or a collection is named by its title
			assert(page.members.map(_.name) === Seq("Nested collection", "Test document", "test_data.csv"))
			assert(page.parentCollections === Nil)
		}
	}

	describe("station landing page") {
		lazy val (page, counts) = build(_.station(fixture.stationUri))

		it("reads the station and its memberships with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 96, existence = 4, sparql = 0))
		}

		it("is built into a station with its location and country") {
			val station = page.org
			assert(station.id === "TST")
			assert(station.org.self.uri === fixture.stationResource)
			assert(station.org.name === "Test station")
			assert(station.location === Some(Position(56.1, 13.4, Some(150f), Some("TST"), None)))
			assert(station.countryCode.map(_.code) === Some("SE"))
			assert(station.responsibleOrganization.map(_.name) === Some("Carbon Portal"))
			assert(station.specificInfo.isInstanceOf[AtcStationSpecifics])
		}

		it("is built with the station's staff") {
			assert(page.staff.map(_.person.self.label) === Seq(Some("Test Person")))
			assert(page.staff.map(_.role.role.label) === Seq(Some("PI")))
			assert(page.staff.map(_.role.start) === Seq(Some(fixture.acqStart)))
			assert(page.currentStaff.size === 1)
			assert(page.formerStaff === Nil)
		}
	}

	describe("organization landing page") {
		lazy val (page, counts) = build(_.organization(fixture.orgUri))

		it("reads the organization and its memberships with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 7, existence = 0, sparql = 0))
		}

		it("is built into an organization without staff of its own") {
			assert(page.org.self.label === Some("CP"))
			assert(page.org.name === "Carbon Portal")
			assert(page.org.email === None)
			assert(page.org.website === None)
			//the only membership in the fixture is at the station, not at this organization
			assert(page.staff === Nil)
		}
	}

	describe("person landing page") {
		lazy val (page, counts) = build(_.person(fixture.personUri))

		it("reads the person and their roles with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 17, existence = 0, sparql = 0))
		}

		it("is built into a person with their role at the station") {
			assert(page.person.self.uri === fixture.personResource)
			assert(page.person.firstName === "Test")
			assert(page.person.lastName === "Person")
			assert(page.person.orcid === None)
			assert(page.roles.map(_.org.label) === Seq(Some("TST")))
			assert(page.roles.map(_.role.role.label) === Seq(Some("PI")))
		}
	}

	describe("instrument landing page") {
		lazy val (page, counts) = build(_.instrument(fixture.instrumentUri))

		it("reads the instrument with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 66, existence = 1, sparql = 0))
		}

		it("is built into an instrument with its model, serial number, owner, vendor, parts and deployments") {
			assert(page.self.uri === fixture.instrumentResource)
			assert(page.self.label === Some("Test instrument"))
			assert(page.model === "Picarro G2401")
			assert(page.serialNumber === "SN-1")
			assert(page.name === Some("Test instrument"))
			assert(page.owner.map(_.name) === Some("Carbon Portal"))
			assert(page.vendor.map(_.name) === Some("Picarro Inc."))
			assert(page.parts.map(_.uri) === Seq(fixture.instrumentPartResource))
			assert(page.partOf === None)
			assert(page.deployments.map(_.start).toSet === Set(
				Some(Instant.parse("2020-06-01T00:00:00Z")), Some(Instant.parse("2018-01-01T00:00:00Z"))
			))
		}
	}

	describe("object specification metadata") {
		lazy val (page, counts) = build(_.specification(fixture.specUri))

		it("reads the specification with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 29, existence = 0, sparql = 0))
		}

		it("is built into a specification with project, theme, format and encoding") {
			assert(page.self.uri === fixture.specResource)
			assert(page.self.label === Some("Test time series"))
			assert(page.dataLevel === 2)
			assert(page.specificDatasetType === DatasetType.StationTimeSeries)
			assert(page.project.self.label === Some("ICOS"))
			assert(page.theme.self.uri === fixture.themeResource)
			assert(page.theme.icon === URI("https://static.icos-cp.eu/atmosphere.svg"))
			assert(page.format.self.label === Some("ASCII CSV time series"))
			assert(page.encoding.label === Some("plain text"))
			assert(page.datasetSpec.map(_.self.label) === Some(Some("Test time series dataset")))
			assert(page.documentation.map(_.res) === Seq(fixture.specDocResource))
			assert(page.keywords === None)
		}

		it("recognizes the specification with a single existence check") {
			val (recognized, counts) = counted(_.isObjectSpecification(fixture.specUri))
			assert(recognized)
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))
		}
	}

	describe("labeled resource metadata") {
		lazy val (page, counts) = build(_.labeledResource(fixture.themeUri))

		it("reads the labeled resource with the expected number of RDF-store queries") {
			assert(counts === QueryCounts(connections = 1, statements = 2, existence = 0, sparql = 0))
		}

		it("is built into the URI, label and comments of the resource") {
			assert(page === UriResource(fixture.themeResource, Some("Atmosphere"), Nil))
		}

		it("recognizes the labeled resource with a single existence check") {
			val (recognized, counts) = counted(_.isLabeledResource(fixture.themeUri))
			assert(recognized)
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))
		}
	}

	describe("generic (fallback) resource page") {
		lazy val (page, counts) = counted(_.genericResource(fixture.stationUri).get)

		it("is served by exactly two SPARQL queries") {
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 2))
		}

		it("is built into the properties, types and usages of the resource") {
			assert(!page.isEmpty)
			assert(page.res === UriResource(fixture.stationResource, Some("TST"), Seq("Test station description")))
			assert(page.types.map(_.uri) === List(fixture.stationClassResource))

			val props = page.propValues.map((prop, value) => prop.uri.toString -> value)
			assert(props.contains(fixture.metaVocab.hasStationId.stringValue -> Right("TST")))
			assert(props.contains(fixture.metaVocab.hasName.stringValue -> Right("Test station")))

			//the acquisition of the data object, and the membership of the person, point at the station
			val usages = page.usage.map((subj, prop) => subj.uri.toString -> prop.uri.toString)
			assert(usages.contains(
				s"http://meta.icos-cp.eu/resources/acq_${fixture.dobjHash.id}" ->
					fixture.metaVocab.prov.wasAssociatedWith.stringValue
			))
			assert(usages.exists((_, prop) => prop == fixture.metaVocab.atOrganization.stringValue))
		}
	}
}


object LandingPageLoaderTests {

	given Envri = Envri.ICOS

	val config = ConfigLoader.default
	given EnvriConfigs = config.core.envriConfigs
	private given EnvriConfig = config.core.envriConfigs(Envri.ICOS)

	/**
	 * Loads the shared metadata fixture. Its named graphs are the production ones, so that the RDF
	 * lenses from the default config see the fixture exactly like they see real metadata.
	 */
	final class Fixture {
		val repo: Repository = SailRepository(MemoryStore())
		repo.init()
		Using.resources(
			getClass.getResourceAsStream("/linkeddata/uri-serializer-fixture.trig"),
			repo.getConnection()
		) { (stream, conn) =>
			conn.add(stream, "", RDFFormat.TRIG)
		}

		val vocab = CpVocab(repo.getValueFactory)
		val metaVocab = CpmetaVocab(repo.getValueFactory)

		private def hash(seed: Byte) = Sha256Sum.fromBytes(Array.fill(18)(seed)).get
		val dobjHash = hash(1)
		val docHash = hash(2)
		val collHash = hash(3)

		private val org = vocab.cp
		private val station = vocab.getStation(UriId("TST"))
		private val person = vocab.getPerson(UriId("Test_Person"))
		private val instrument = vocab.getInstrument(UriId("TST_1"))
		private val spec = vocab.getObjectSpecification(UriId("testTimeSeries"))
		private val dobj = vocab.getStaticObject(dobjHash)
		private val doc = vocab.getStaticObject(docHash)
		private val coll = vocab.getCollection(collHash)
		private val nestedColl = vocab.getCollection(hash(12))
		private val specDoc = vocab.getStaticObject(hash(17))

		val stationUri = Uri(station.stringValue)
		val orgUri = Uri(org.stringValue)
		val personUri = Uri(person.stringValue)
		val instrumentUri = Uri(instrument.stringValue)
		val specUri = Uri(spec.stringValue)
		val themeUri = Uri(vocab.atmoTheme.stringValue)

		val dobjResource = URI(dobj.stringValue)
		val docResource = URI(doc.stringValue)
		val collResource = URI(coll.stringValue)
		val nestedCollResource = URI(nestedColl.stringValue)
		val specDocResource = URI(specDoc.stringValue)
		val stationResource = URI(station.stringValue)
		val personResource = URI(person.stringValue)
		val instrumentResource = URI(instrument.stringValue)
		val instrumentPartResource = URI(vocab.getInstrument(UriId("TST_2")).stringValue)
		val specResource = URI(spec.stringValue)
		val themeResource = URI(vocab.atmoTheme.stringValue)
		val stationClassResource = URI(metaVocab.atmoStationClass.stringValue)

		val dobjAccessUrl = vocab.getStaticObjectAccessUrl(dobjHash)
		val docAccessUrl = vocab.getStaticObjectAccessUrl(docHash)

		val acqStart = Instant.parse("2021-01-01T00:00:00Z")
		val acqStop = Instant.parse("2021-12-31T23:59:59Z")
		val submStart = Instant.parse("2022-01-02T10:00:00Z")
		val submStop = Instant.parse("2022-01-02T11:00:00Z")
	}
}
