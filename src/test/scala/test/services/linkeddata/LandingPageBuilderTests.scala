package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.query.{QueryLanguage, TupleQuery}
import org.eclipse.rdf4j.repository.base.{RepositoryConnectionWrapper, RepositoryWrapper}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection, RepositoryResult}
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{PidFactory, UriId}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{
	AtcStationSpecifics, DataObject, DatasetType, DocObject, EnvriConfig, EnvriConfigs, Position,
	StaticObject, TimeInterval, UriResource
}
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataClient
import se.lu.nateko.cp.meta.services.linkeddata.LandingPageBuilder
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}

import java.net.URI
import java.time.{Instant, LocalDate}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext
import scala.util.Using

/**
 * Guards the number of RDF-store round trips every landing page costs.
 *
 * The builder is given an in-memory triplestore with a hand-made, minimal-but-complete metadata
 * fixture, wrapped in a proxy that counts every statement lookup, existence check and SPARQL
 * query made through it. Each expectation below is therefore a snapshot of how chatty one page
 * is: a change in the counts means the read path changed, and the new number has to be looked at
 * (and only then written down here) rather than silently accepted.
 */
class LandingPageBuilderTests extends AnyFunSpec with BeforeAndAfterAll:

	import LandingPageBuilderTests.{*, given}

	private given system: ActorSystem = ActorSystem("LandingPageBuilderTests")
	private given Materializer = Materializer.matFromSystem
	private given ExecutionContext = system.dispatcher

	private val counter = QueryCounter()
	private val fixture = Fixture()
	private val builder = LandingPageBuilder(
		CountingRepository(fixture.repo, counter),
		fixture.vocab,
		fixture.metaVocab,
		MetaDb.getLenses(config.instanceServers, config.dataUploadService),
		PidFactory(config.dataUploadService.handle.baseUrl, config.dataUploadService.handle.prefix),
		// only reached by the *WithDerived methods, which this test deliberately avoids:
		// derived metadata is an HTTP call to rdfStore, not an RDF-store query
		DerivedMetadataClient(URI("http://localhost:1/derived/v1/resolve"))
	)

	override def afterAll(): Unit =
		fixture.repo.shutDown()
		system.terminate()

	/**
	 * Builds one page, requiring it to have been built without errors, and captures the query
	 * counts of that build. Called from a `lazy val` so that the counts always belong to the
	 * build, no matter which of the tests sharing the page happens to run first.
	 */
	private def build[T](page: => Validated[T]): (T, QueryCounts) =
		counter.reset()
		val built = page
		val counts = counter.snapshot
		assert(built.errors === Nil)
		(built.result.getOrElse(fail("the page was not built at all")), counts)

	private def asDataObject(obj: StaticObject): DataObject = obj match
		case dobj: DataObject => dobj
		case other => fail(s"Expected a DataObject, got $other")

	private def asDocObject(obj: StaticObject): DocObject = obj match
		case doc: DocObject => doc
		case other => fail(s"Expected a DocObject, got $other")

	describe("data object landing page"):
		lazy val (page, counts) = build(builder.staticObject(fixture.dobjHash))

		it("reads the object in four bounded RDF-store queries"):
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 4))

		it("has the file-level metadata of the object"):
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

		it("has the specification of the object"):
			val spec = asDataObject(page).specification
			assert(spec.self.uri === fixture.specResource)
			assert(spec.self.label === Some("Test time series"))
			assert(spec.dataLevel === 2)
			assert(spec.specificDatasetType === DatasetType.StationTimeSeries)
			assert(spec.project.self.label === Some("ICOS"))
			assert(spec.theme.self.label === Some("Atmosphere"))
			assert(spec.format.self.label === Some("ASCII CSV time series"))
			assert(spec.encoding.label === Some("plain text"))

		it("has the station time series acquisition metadata"):
			val l2 = asDataObject(page).specificInfo match
				case Right(stationTimeSeries) => stationTimeSeries
				case Left(spatioTemporal) => fail(s"Expected station time series metadata, got $spatioTemporal")
			assert(l2.nRows === Some(100))
			assert(l2.columns === None)
			assert(l2.productionInfo === None)
			assert(l2.acquisition.station.id === "TST")
			assert(l2.acquisition.station.org.name === "Test station")
			assert(l2.acquisition.interval === Some(TimeInterval(fixture.acqStart, fixture.acqStop)))
			assert(l2.acquisition.samplingHeight === Some(50f))
			assert(l2.acquisition.instruments.map(_.label) === Seq(Some("Test instrument")))

		it("has the ICOS specifics of the station it was acquired at"):
			val station = asDataObject(page).specificInfo match
				case Right(stationTimeSeries) => stationTimeSeries.acquisition.station
				case Left(spatioTemporal) => fail(s"Expected station time series metadata, got $spatioTemporal")
			//as on the station page, these come from the thematic centre and the labeling app,
			//neither of which is reachable from the object by a link
			val specifics = station.specificInfo match
				case atc: AtcStationSpecifics => atc
				case other => fail(s"Expected ATC station specifics, got $other")
			assert(specifics.theme.map(_.self.uri) === Some(fixture.themeResource))
			assert(specifics.labelingDate === Some(LocalDate.of(2019, 6, 1)))

		it("knows the collection the object is a part of, and that it is the only version"):
			val dobj = asDataObject(page)
			assert(dobj.parentCollections.map(_.label) === Seq(Some("Test collection")))
			assert(dobj.previousVersion === None)
			assert(dobj.nextVersion === None)
			assert(dobj.latestVersion === Left(fixture.dobjResource))

	describe("document object landing page"):
		lazy val (page, counts) = build(builder.staticObject(fixture.docHash))

		it("reads the document in three bounded RDF-store queries"):
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 3))

		it("is built into a document object with its title and authors"):
			val doc = asDocObject(page)
			assert(doc.hash === fixture.docHash)
			assert(doc.fileName === "test_doc.pdf")
			assert(doc.size === Some(54321L))
			assert(doc.accessUrl === Some(fixture.docAccessUrl))
			assert(doc.pid === Some(s"11676/${fixture.docHash.id}"))
			assert(doc.description === None)
			assert(doc.references.title === Some("Test document"))
			assert(doc.references.authors.map(_.map(_.self.label)) === Some(Seq(Some("Test Person"))))
			assert(doc.submission.submitter.name === "Carbon Portal")
			assert(doc.parentCollections.map(_.label) === Seq(Some("Test collection")))

	describe("collection landing page"):
		lazy val (page, counts) = build(builder.staticCollection(fixture.collHash))

		it("reads the collection in three bounded RDF-store queries"):
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 3))

		it("is built into a collection with both of its members"):
			assert(page.res === fixture.collResource)
			assert(page.hash === fixture.collHash)
			assert(page.title === "Test collection")
			assert(page.description === Some("A collection of test items"))
			assert(page.creator.name === "Carbon Portal")
			assert(page.doi === None)
			assert(page.members.map(_.res).toSet === Set(fixture.dobjResource, fixture.docResource))
			//members are sorted by name, and a document object is named by its title
			assert(page.members.map(_.name) === Seq("Test document", "test_data.csv"))
			assert(page.parentCollections === Nil)

	describe("station landing page"):
		lazy val (page, counts) = build(builder.station(fixture.stationUri))

		it("reads the station and its memberships in three bounded RDF-store queries"):
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 3))

		it("is built into a station with its location and country"):
			val station = page.org
			assert(station.id === "TST")
			assert(station.org.self.uri === fixture.stationResource)
			assert(station.org.name === "Test station")
			assert(station.location === Some(Position(56.1, 13.4, Some(150f), Some("TST"), None)))
			assert(station.countryCode.map(_.code) === Some("SE"))
			assert(station.responsibleOrganization === None)

		it("has the ICOS specifics the station's thematic centre and the labeling app supply"):
			val specifics = page.org.specificInfo match
				case atc: AtcStationSpecifics => atc
				case other => fail(s"Expected ATC station specifics, got $other")
			//neither of these is reachable from the station by a link
			assert(specifics.theme.map(_.self.uri) === Some(fixture.themeResource))
			assert(specifics.labelingDate === Some(LocalDate.of(2019, 6, 1)))

		it("is built with the station's staff"):
			assert(page.staff.map(_.person.self.label) === Seq(Some("Test Person")))
			assert(page.staff.map(_.role.role.label) === Seq(Some("PI")))
			assert(page.staff.map(_.role.start) === Seq(Some(fixture.acqStart)))
			assert(page.currentStaff.size === 1)
			assert(page.formerStaff === Nil)

	describe("organization landing page"):
		lazy val (page, counts) = build(builder.organization(fixture.orgUri))

		it("reads the organization and its memberships"):
			assert(counts === QueryCounts(connections = 1, statements = 7, existence = 0, sparql = 0))

		it("is built into an organization without staff of its own"):
			assert(page.org.self.label === Some("CP"))
			assert(page.org.name === "Carbon Portal")
			assert(page.org.email === None)
			assert(page.org.website === None)
			//the only membership in the fixture is at the station, not at this organization
			assert(page.staff === Nil)

	describe("person landing page"):
		lazy val (page, counts) = build(builder.person(fixture.personUri))

		it("reads the person and their roles"):
			assert(counts === QueryCounts(connections = 1, statements = 17, existence = 0, sparql = 0))

		it("is built into a person with their role at the station"):
			assert(page.person.self.uri === fixture.personResource)
			assert(page.person.firstName === "Test")
			assert(page.person.lastName === "Person")
			assert(page.person.orcid === None)
			assert(page.roles.map(_.org.label) === Seq(Some("TST")))
			assert(page.roles.map(_.role.role.label) === Seq(Some("PI")))

	describe("instrument landing page"):
		lazy val (page, counts) = build(builder.instrument(fixture.instrumentUri))

		it("reads the instrument"):
			assert(counts === QueryCounts(connections = 1, statements = 18, existence = 1, sparql = 0))

		it("is built into an instrument with its model, serial number and owner"):
			assert(page.self.uri === fixture.instrumentResource)
			assert(page.self.label === Some("Test instrument"))
			assert(page.model === "Picarro G2401")
			assert(page.serialNumber === "SN-1")
			assert(page.name === Some("Test instrument"))
			assert(page.owner.map(_.name) === Some("Carbon Portal"))
			assert(page.vendor === None)
			assert(page.parts === Nil)
			assert(page.partOf === None)
			assert(page.deployments === Nil)

	describe("object specification landing page"):
		lazy val (page, counts) = build(builder.specification(fixture.specUri))

		it("reads the specification"):
			assert(counts === QueryCounts(connections = 1, statements = 23, existence = 0, sparql = 0))

		it("is built into a specification with project, theme, format and encoding"):
			assert(page.self.uri === fixture.specResource)
			assert(page.self.label === Some("Test time series"))
			assert(page.dataLevel === 2)
			assert(page.specificDatasetType === DatasetType.StationTimeSeries)
			assert(page.project.self.label === Some("ICOS"))
			assert(page.theme.self.uri === fixture.themeResource)
			assert(page.theme.icon === URI("https://static.icos-cp.eu/atmosphere.svg"))
			assert(page.format.self.label === Some("ASCII CSV time series"))
			assert(page.encoding.label === Some("plain text"))
			assert(page.datasetSpec === None)
			assert(page.documentation === Nil)
			assert(page.keywords === None)

		it("recognizes the specification with a single existence check"):
			counter.reset()
			assert(builder.isObjectSpecification(fixture.specUri))
			assert(counter.snapshot === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))

	describe("labeled resource landing page"):
		lazy val (page, counts) = build(builder.labeledResource(fixture.themeUri))

		it("reads the labeled resource"):
			assert(counts === QueryCounts(connections = 1, statements = 2, existence = 0, sparql = 0))

		it("is built into the URI, label and comments of the resource"):
			assert(page === UriResource(fixture.themeResource, Some("Atmosphere"), Nil))

		it("recognizes the labeled resource with a single existence check"):
			counter.reset()
			assert(builder.isLabeledResource(fixture.themeUri))
			assert(counter.snapshot === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))

	describe("generic (fallback) resource page"):
		lazy val (page, counts) =
			counter.reset()
			val viewInfo = builder.genericResource(fixture.stationUri).get
			viewInfo -> counter.snapshot

		it("is served by exactly two SPARQL queries"):
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 2))

		it("is built into the properties, types and usages of the resource"):
			assert(!page.isEmpty)
			assert(page.res === UriResource(fixture.stationResource, Some("TST"), Nil))
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

end LandingPageBuilderTests


object LandingPageBuilderTests:

	given Envri = Envri.ICOS

	val config = ConfigLoader.default
	given EnvriConfigs = config.core.envriConfigs
	private given EnvriConfig = config.core.envriConfigs(Envri.ICOS)

	case class QueryCounts(connections: Int, statements: Int, existence: Int, sparql: Int)

	/** Counts the reads the builder makes against the triplestore, per kind. */
	final class QueryCounter:
		private val connections = AtomicInteger()
		private val statements = AtomicInteger()
		private val existence = AtomicInteger()
		private val sparql = AtomicInteger()

		def countConnection(): Unit = connections.incrementAndGet()
		def countStatements(): Unit = statements.incrementAndGet()
		def countExistence(): Unit = existence.incrementAndGet()
		def countSparql(): Unit = sparql.incrementAndGet()

		def reset(): Unit = List(connections, statements, existence, sparql).foreach(_.set(0))

		def snapshot: QueryCounts =
			QueryCounts(connections.get, statements.get, existence.get, sparql.get)

	final class CountingRepository(delegate: Repository, counter: QueryCounter) extends RepositoryWrapper(delegate):
		override def getConnection(): RepositoryConnection =
			counter.countConnection()
			CountingConnection(this, super.getConnection(), counter)

	final class CountingConnection(
		repo: Repository, delegate: RepositoryConnection, counter: QueryCounter
	) extends RepositoryConnectionWrapper(repo, delegate):

		override def getStatements(
			subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
		): RepositoryResult[org.eclipse.rdf4j.model.Statement] =
			counter.countStatements()
			super.getStatements(subj, pred, obj, includeInferred, contexts*)

		override def hasStatement(
			subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
		): Boolean =
			counter.countExistence()
			super.hasStatement(subj, pred, obj, includeInferred, contexts*)

		override def prepareTupleQuery(ql: QueryLanguage, query: String, baseURI: String): TupleQuery =
			counter.countSparql()
			super.prepareTupleQuery(ql, query, baseURI)

	/**
	 * A minimal metadata fixture: one data object, one document object and one collection, plus
	 * the station, person, instrument, organization and specification they refer to. The named
	 * graphs are the production ones, so that the RDF lenses from the default config see the
	 * fixture exactly like they see real metadata.
	 */
	final class Fixture:
		val repo: Repository = SailRepository(MemoryStore())
		repo.init()

		val vocab = CpVocab(repo.getValueFactory)
		val metaVocab = CpmetaVocab(repo.getValueFactory)

		private def hash(seed: Byte) = Sha256Sum.fromBytes(Array.fill(18)(seed)).get
		val dobjHash = hash(1)
		val docHash = hash(2)
		val collHash = hash(3)

		private val station = vocab.getStation(UriId("TST"))
		private val person = vocab.getPerson(UriId("Test_Person"))
		private val instrument = vocab.getInstrument(UriId("TST_1"))
		private val spec = vocab.getObjectSpecification(UriId("testTimeSeries"))
		val stationUri = Uri(station.stringValue)
		val orgUri = Uri(vocab.cp.stringValue)
		val personUri = Uri(person.stringValue)
		val instrumentUri = Uri(instrument.stringValue)
		val specUri = Uri(spec.stringValue)
		val themeUri = Uri(vocab.atmoTheme.stringValue)

		val dobjResource = URI(vocab.getStaticObject(dobjHash).stringValue)
		val docResource = URI(vocab.getStaticObject(docHash).stringValue)
		val collResource = URI(vocab.getCollection(collHash).stringValue)
		val stationResource = URI(station.stringValue)
		val personResource = URI(person.stringValue)
		val instrumentResource = URI(instrument.stringValue)
		val specResource = URI(spec.stringValue)
		val themeResource = URI(vocab.atmoTheme.stringValue)
		val stationClassResource = URI(metaVocab.atmoStationClass.stringValue)

		val dobjAccessUrl = vocab.getStaticObjectAccessUrl(dobjHash)
		val docAccessUrl = vocab.getStaticObjectAccessUrl(docHash)

		val acqStart = Instant.parse("2021-01-01T00:00:00Z")
		val acqStop = Instant.parse("2021-12-31T23:59:59Z")
		val submStart = Instant.parse("2022-01-02T10:00:00Z")
		val submStop = Instant.parse("2022-01-02T11:00:00Z")

		Using.resources(
			getClass.getResourceAsStream("/linkeddata/landing-page-builder-fixture.trig"),
			repo.getConnection()
		): (stream, conn) =>
			conn.add(stream, "", RDFFormat.TRIG)

	end Fixture

end LandingPageBuilderTests
