package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS, XSD}
import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.query.{QueryLanguage, TupleQuery}
import org.eclipse.rdf4j.repository.base.{RepositoryConnectionWrapper, RepositoryWrapper}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection, RepositoryResult}
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{PidFactory, UriId}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataClient
import se.lu.nateko.cp.meta.services.linkeddata.LandingPageBuilder
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}

import java.net.URI
import java.time.Instant
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

	/** Builds one page, requires it to have been built without errors, and returns the query counts. */
	private def countQueries[T](page: => Validated[T]): QueryCounts =
		counter.reset()
		val built = page
		val counts = counter.snapshot
		assert(built.errors === Nil)
		assert(built.result.isDefined)
		counts

	describe("data object landing page"):
		it("reads the object with the expected number of RDF-store queries"):
			val counts = countQueries(builder.staticObject(fixture.dobjHash))
			assert(counts === QueryCounts(connections = 1, statements = 91, existence = 6, sparql = 0))

	describe("document object landing page"):
		it("reads the document with the expected number of RDF-store queries"):
			val counts = countQueries(builder.staticObject(fixture.docHash))
			assert(counts === QueryCounts(connections = 1, statements = 33, existence = 2, sparql = 0))

	describe("collection landing page"):
		it("reads the collection with the expected number of RDF-store queries"):
			val counts = countQueries(builder.staticCollection(fixture.collHash))
			assert(counts === QueryCounts(connections = 1, statements = 23, existence = 3, sparql = 0))

	describe("station landing page"):
		it("reads the station and its memberships with the expected number of RDF-store queries"):
			val counts = countQueries(builder.station(fixture.stationUri))
			assert(counts === QueryCounts(connections = 1, statements = 40, existence = 3, sparql = 0))

	describe("organization landing page"):
		it("reads the organization and its memberships with the expected number of RDF-store queries"):
			val counts = countQueries(builder.organization(fixture.orgUri))
			assert(counts === QueryCounts(connections = 1, statements = 7, existence = 0, sparql = 0))

	describe("person landing page"):
		it("reads the person and their roles with the expected number of RDF-store queries"):
			val counts = countQueries(builder.person(fixture.personUri))
			assert(counts === QueryCounts(connections = 1, statements = 17, existence = 0, sparql = 0))

	describe("instrument landing page"):
		it("reads the instrument with the expected number of RDF-store queries"):
			val counts = countQueries(builder.instrument(fixture.instrumentUri))
			assert(counts === QueryCounts(connections = 1, statements = 18, existence = 1, sparql = 0))

	describe("object specification landing page"):
		it("reads the specification with the expected number of RDF-store queries"):
			val counts = countQueries(builder.specification(fixture.specUri))
			assert(counts === QueryCounts(connections = 1, statements = 23, existence = 0, sparql = 0))

		it("recognizes the specification with a single existence check"):
			counter.reset()
			assert(builder.isObjectSpecification(fixture.specUri))
			assert(counter.snapshot === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))

	describe("labeled resource landing page"):
		it("reads the labeled resource with the expected number of RDF-store queries"):
			val counts = countQueries(builder.labeledResource(fixture.themeUri))
			assert(counts === QueryCounts(connections = 1, statements = 2, existence = 0, sparql = 0))

		it("recognizes the labeled resource with a single existence check"):
			counter.reset()
			assert(builder.isLabeledResource(fixture.themeUri))
			assert(counter.snapshot === QueryCounts(connections = 1, statements = 0, existence = 1, sparql = 0))

	describe("generic (fallback) resource page"):
		it("is served by exactly two SPARQL queries"):
			counter.reset()
			val viewInfo = builder.genericResource(fixture.stationUri)
			val counts = counter.snapshot
			assert(viewInfo.isSuccess)
			assert(!viewInfo.get.isEmpty)
			assert(counts === QueryCounts(connections = 1, statements = 0, existence = 0, sparql = 2))

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
		private val factory = repo.getValueFactory
		private def iri(uri: String): IRI = factory.createIRI(uri)

		private val cpmetaGraph = iri("http://meta.icos-cp.eu/resources/cpmeta/")
		private val metaGraph = iri("http://meta.icos-cp.eu/resources/icos/")
		private val docsGraph = iri("http://meta.icos-cp.eu/documents/")
		private val collsGraph = iri("http://meta.icos-cp.eu/collections/")
		//the RDF graph of the 'tscsv' data-object instance server (format csvWithIso8601tsFirstCol)
		private val dobjGraph = iri("http://meta.icos-cp.eu/resources/tscsv/")

		private def hash(seed: Byte) = Sha256Sum.fromBytes(Array.fill(18)(seed)).get
		val dobjHash = hash(1)
		val docHash = hash(2)
		val collHash = hash(3)

		private val org = vocab.cp
		private val station = vocab.getStation(UriId("TST"))
		private val person = vocab.getPerson(UriId("Test_Person"))
		private val membership = vocab.getMembership(UriId("TST_PI_Person"))
		private val role = iri("http://meta.icos-cp.eu/resources/roles/PI")
		private val instrument = vocab.getInstrument(UriId("TST_1"))
		private val spec = vocab.getObjectSpecification(UriId("testTimeSeries"))
		private val format = iri("http://meta.icos-cp.eu/ontologies/cpmeta/csvWithIso8601tsFirstCol")
		private val encoding = iri("http://meta.icos-cp.eu/ontologies/cpmeta/asciiEncoding")
		private val dobj = vocab.getStaticObject(dobjHash)
		private val acquisition = vocab.getAcquisition(dobjHash)
		private val submission = vocab.getSubmission(dobjHash)
		private val doc = vocab.getStaticObject(docHash)
		private val docSubmission = vocab.getSubmission(docHash)
		private val coll = vocab.getCollection(collHash)

		val stationUri = Uri(station.stringValue)
		val orgUri = Uri(org.stringValue)
		val personUri = Uri(person.stringValue)
		val instrumentUri = Uri(instrument.stringValue)
		val specUri = Uri(spec.stringValue)
		val themeUri = Uri(vocab.atmoTheme.stringValue)

		private val acqStart = Instant.parse("2021-01-01T00:00:00Z")
		private val acqStop = Instant.parse("2021-12-31T23:59:59Z")
		private val submStart = Instant.parse("2022-01-02T10:00:00Z")
		private val submStop = Instant.parse("2022-01-02T11:00:00Z")

		Using.resource(repo.getConnection()): conn =>
			def add(graph: IRI)(triples: (IRI, IRI, Value)*): Unit =
				triples.foreach: (subj, pred, obj) =>
					conn.add(subj, pred, obj, graph)

			add(cpmetaGraph)(
				(vocab.icosProject, RDFS.LABEL, vocab.lit("ICOS")),
				(vocab.atmoTheme, RDFS.LABEL, vocab.lit("Atmosphere")),
				(vocab.atmoTheme, metaVocab.hasIcon, vocab.lit(URI("https://static.icos-cp.eu/atmosphere.svg"))),
				(format, RDFS.LABEL, vocab.lit("ASCII CSV time series")),
				(encoding, RDFS.LABEL, vocab.lit("plain text")),
				(role, RDF.TYPE, metaVocab.roleClass),
				(role, RDFS.LABEL, vocab.lit("PI")),
				(org, RDF.TYPE, metaVocab.orgClass),
				(org, RDFS.LABEL, vocab.lit("CP")),
				(org, metaVocab.hasName, vocab.lit("Carbon Portal")),
				(spec, RDF.TYPE, metaVocab.dataObjectSpecClass),
				(spec, RDFS.LABEL, vocab.lit("Test time series")),
				(spec, metaVocab.hasAssociatedProject, vocab.icosProject),
				(spec, metaVocab.hasDataTheme, vocab.atmoTheme),
				(spec, metaVocab.hasFormat, format),
				(spec, metaVocab.hasEncoding, encoding),
				(spec, metaVocab.hasSpecificDatasetType, metaVocab.stationTimeSeriesDs),
				(spec, metaVocab.hasDataLevel, vocab.lit(2))
			)

			add(metaGraph)(
				(station, RDF.TYPE, metaVocab.atmoStationClass),
				(station, RDFS.LABEL, vocab.lit("TST")),
				(station, metaVocab.hasName, vocab.lit("Test station")),
				(station, metaVocab.hasStationId, vocab.lit("TST")),
				(station, metaVocab.hasLatitude, vocab.lit(56.1)),
				(station, metaVocab.hasLongitude, vocab.lit(13.4)),
				(station, metaVocab.hasElevation, vocab.lit(150f)),
				(station, metaVocab.countryCode, vocab.lit("SE")),
				(person, RDF.TYPE, metaVocab.personClass),
				(person, RDFS.LABEL, vocab.lit("Test Person")),
				(person, metaVocab.hasFirstName, vocab.lit("Test")),
				(person, metaVocab.hasLastName, vocab.lit("Person")),
				(person, metaVocab.hasMembership, membership),
				(membership, RDF.TYPE, metaVocab.membershipClass),
				(membership, metaVocab.atOrganization, station),
				(membership, metaVocab.hasRole, role),
				(membership, metaVocab.hasStartTime, vocab.lit(acqStart)),
				(instrument, RDF.TYPE, metaVocab.instrumentClass),
				(instrument, metaVocab.hasName, vocab.lit("Test instrument")),
				(instrument, metaVocab.hasModel, vocab.lit("Picarro G2401")),
				(instrument, metaVocab.hasSerialNumber, vocab.lit("SN-1")),
				(instrument, metaVocab.hasInstrumentOwner, org)
			)

			add(dobjGraph)(
				(dobj, RDF.TYPE, metaVocab.dataObjectClass),
				(dobj, metaVocab.hasObjectSpec, spec),
				(dobj, metaVocab.hasSha256sum, vocab.lit(dobjHash.base64, XSD.BASE64BINARY)),
				(dobj, metaVocab.hasName, vocab.lit("test_data.csv")),
				(dobj, metaVocab.hasSizeInBytes, vocab.lit(12345L)),
				(dobj, metaVocab.hasNumberOfRows, vocab.lit(100)),
				(dobj, metaVocab.wasSubmittedBy, submission),
				(dobj, metaVocab.wasAcquiredBy, acquisition),
				(submission, RDF.TYPE, metaVocab.submissionClass),
				(submission, metaVocab.prov.wasAssociatedWith, org),
				(submission, metaVocab.prov.startedAtTime, vocab.lit(submStart)),
				(submission, metaVocab.prov.endedAtTime, vocab.lit(submStop)),
				(acquisition, RDF.TYPE, metaVocab.aquisitionClass),
				(acquisition, metaVocab.prov.wasAssociatedWith, station),
				(acquisition, metaVocab.prov.startedAtTime, vocab.lit(acqStart)),
				(acquisition, metaVocab.prov.endedAtTime, vocab.lit(acqStop)),
				(acquisition, metaVocab.hasSamplingHeight, vocab.lit(50f)),
				(acquisition, metaVocab.wasPerformedWith, instrument)
			)

			add(docsGraph)(
				(doc, RDF.TYPE, metaVocab.docObjectClass),
				(doc, metaVocab.hasSha256sum, vocab.lit(docHash.base64, XSD.BASE64BINARY)),
				(doc, metaVocab.hasName, vocab.lit("test_doc.pdf")),
				(doc, metaVocab.hasSizeInBytes, vocab.lit(54321L)),
				(doc, metaVocab.dcterms.title, vocab.lit("Test document")),
				(doc, metaVocab.dcterms.creator, person),
				(doc, metaVocab.wasSubmittedBy, docSubmission),
				(docSubmission, RDF.TYPE, metaVocab.submissionClass),
				(docSubmission, metaVocab.prov.wasAssociatedWith, org),
				(docSubmission, metaVocab.prov.startedAtTime, vocab.lit(submStart)),
				(docSubmission, metaVocab.prov.endedAtTime, vocab.lit(submStop))
			)

			add(collsGraph)(
				(coll, RDF.TYPE, metaVocab.collectionClass),
				(coll, metaVocab.dcterms.title, vocab.lit("Test collection")),
				(coll, metaVocab.dcterms.description, vocab.lit("A collection of test items")),
				(coll, metaVocab.dcterms.creator, org),
				(coll, metaVocab.dcterms.hasPart, dobj),
				(coll, metaVocab.dcterms.hasPart, doc)
			)

	end Fixture

end LandingPageBuilderTests
