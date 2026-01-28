package se.lu.nateko.cp.meta.test.icos

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{Statement, ValueFactory}
import org.scalatest.GivenWhenThen
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{RdfLens, UriId}
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, Rdf4jInstanceServer}
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.metaflow.icos.{ATC, ETC, AtcConf, EtcConf}
import se.lu.nateko.cp.meta.services.upload.DobjMetaReader
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.{Loading, toRdf}

import java.net.URI

import se.lu.nateko.cp.meta.core.tests.TestFactory.make
import se.lu.nateko.cp.meta.test.MetaTestFactory.{withSpecifics, given}

import RdfDiffCalcTests.*

class RdfDiffCalcTests extends AnyFunSpec with GivenWhenThen:

	given EnvriConfigs = Map(
		Envri.ICOS -> EnvriConfig(null, null, null, null, new URI("http://test.icos.eu/"), null)
	)

	type A = ATC.type
	import AtcConf.{makeId => aId}

	val jane = TcPerson[A](UriId("Jane_Doe"), Some(aId("pers_0")), "Jane", "Doe", Some("jane.doe@icos-ri.eu"), None)
	val se = CountryCode.unapply("SE").get

	val airCpStation = TcStation[A](
		cpId = UriId("AIR1"),
		tcId = aId("43"),
		core = Station(
			org = Organization(
				self = UriResource(new URI("http://test.icos.eu/resources/stations/AIR1"), Some("AIR1"), Seq.empty),
				name = "Airplane 1",
				email = None,
				website = None,
				webpageDetails = None
			),
			id = "AIR1",
			location = None,
			coverage = None,
			responsibleOrganization = None,
			pictures = Seq.empty,
			Some(se),
			specificInfo = AtcStationSpecifics(Some("wigos"), None, None, None, false, None, Nil),
			funding = None
		),
		responsibleOrg = None,
		funding = Nil
	)

	def atcInitSnap(pi: TcPerson[A]): TcState[A] = {
		val piMemb = Membership[A](UriId(""), new AssumedRole(PI, pi, airCpStation, None, None), None, None)
		new TcState[A](stations = Seq(airCpStation), roles = Seq(piMemb), instruments = Nil)
	}

	describe("person name change"){

		Given("starting with an empty state with no own CP statements")

		val state = init(Nil, _ => Nil)

		When("an ATC-state snapshot with single station is inserted")
		val diffV = state.calc.calcDiff(atcInitSnap(jane))
		val initUpdates = diffV.result.get.toIndexedSeq

		it("Then it results in expected sequence of RDF updates"){
			assert(diffV.errors.isEmpty)
			assert(initUpdates.forall(_.isAssertion))
			assert(initUpdates.size >= 13) //the number may change if metadata model changes
		}

		state.tcServer.applyAll(initUpdates)()

		And("reading current TC state back produces expected value")

		it("(has the expected PI, the station and the role)"){
			val sV = state.reader.getCurrentState[A]
			assert(sV.errors.isEmpty)
			val s = sV.result.get
			assert(s.stations.size === 1)
			assert(s.stations.head === airCpStation)
			assert(s.instruments.isEmpty)
			assert(s.roles.size === 1)
			val memb = s.roles.head
			assert(memb.start.isEmpty) //init state was empty, so cannot know when the role was assumed first
			assert(memb.stop.isEmpty) //just created, so cannot have ended
			assert(memb.role.kind === PI)
			assert(memb.role.org === airCpStation)
			assert(memb.role.holder === jane)
		}

		When("afterwards a new snapshot comes with person's last name (and consequently cpId) changed")

		val jane2 = jane.copy(cpId = UriId("Jane_Smith"), lname = "Smith")

		val peopleUpdates = state.calc.calcDiff(atcInitSnap(jane2)).result.get
			.filter(_.statement.getSubject.toString.contains("people")).toIndexedSeq

		it("Then only name-changing updates are applied"){
			assert(peopleUpdates.size === 2)

			val gist = peopleUpdates.map{upd =>
				upd.statement.getObject.stringValue -> upd.isAssertion
			}.toMap

			assert(gist === Map("Doe" -> false, "Smith" -> true))
		}
	}

	describe("Station network associations") {

		val testState: TestState = init(Nil, _ => Nil)

		val stationWithoutNetwork = make[TcStation[ETC.type]].withSpecifics(_.copy(networkNames = Set.empty))
		val etcState = new TcState(stations = Seq(stationWithoutNetwork), roles = Seq(), instruments = Nil)
		testState.tcServer.applyAll(testState.calc.calcDiff(etcState).result.get)()

		val etcStationWithNetwork = stationWithoutNetwork.withSpecifics( _.copy(networkNames = Set("TestNetwork")))
		val tcStateWithNetwork = new TcState(stations = Seq(etcStationWithNetwork), roles = Seq(), instruments = Nil)

		it("skips associatedNetwork triple and logs error when target network does not exist") {
			// Calculate diff - no triples should be added since network doesn't exist
			val triples = testState.calc.calcDiff(tcStateWithNetwork).result.get.toSeq

			// Verify no associatedNetwork triples are produced
			val assocTriples = triples.filter(_.statement.getPredicate.stringValue.endsWith("associatedNetwork"))
			assert(assocTriples.isEmpty, s"Expected no associatedNetwork triples, got ${assocTriples.size}")
		}

		val factory = testState.tcServer.factory
		val networkIriStr = "http://test.icos.eu/resources/networks/TestNetwork"
		val networkIri = factory.createIRI(networkIriStr)

		it("produces associatedNetwork triples when network exists") {
			import org.eclipse.rdf4j.model.vocabulary.RDF
			val metaVocab = testState.maker.meta

			testState.tcServer.addAll(Seq(
				factory.createStatement(networkIri, RDF.TYPE, metaVocab.networkClass),
				factory.createStatement(networkIri, metaVocab.hasName, factory.createLiteral("TestNetwork"))
			))

			val Seq(addTriple) = testState.calc.calcDiff(tcStateWithNetwork).result.get
			assert(addTriple.statement.getPredicate().stringValue() == "http://meta.icos-cp.eu/ontologies/cpmeta/associatedNetwork")
			assert(addTriple.statement.getObject().stringValue() == networkIriStr)
			assert(addTriple.statement.getObject.isInstanceOf[org.eclipse.rdf4j.model.IRI])
			assert(addTriple.isAssertion)

			// Apply the addition
			testState.tcServer.applyAll(Seq(addTriple))()
		}

		it("removes associatedNetwork triple when network is removed") {
			val stateWithoutNetwork = new TcState(stations = Seq(stationWithoutNetwork), roles = Seq(), instruments = Nil)
			val Seq(removeTriple) = testState.calc.calcDiff(stateWithoutNetwork).result.get
			assert(removeTriple.statement.getPredicate.stringValue.endsWith("associatedNetwork"))
			assert(removeTriple.statement.getObject.stringValue == networkIriStr)
			assert(!removeTriple.isAssertion)
		}
	}

	describe("PI change"){

		Given("starting with a single station with single PI and no own CP statements")

		val state = init(Nil, _ => Nil)
		state.tcServer.applyAll(state.calc.calcDiff(atcInitSnap(jane)).result.get)()

		When("a new snapshot comes where the PI has changed")

		val john = TcPerson[A](UriId("John_Brown"), Some(aId("pers_1")), "John", "Brown", Some("john.brown@icos-ri.eu"), None)
		val piUpdates = state.calc.calcDiff(atcInitSnap(john)).result.get.toIndexedSeq
		state.tcServer.applyAll(piUpdates)()

		it("Then previous PI's membership stays and the new ones' is created and started"){
			assert(piUpdates.size >= 10)
			val membs = state.reader.getCurrentState[A].result.get.roles
			assert(membs.size === 2)
			val johnMemb = membs.find(_.role.holder == john).get
			val janeMemb = membs.find(_.role.holder == jane).get
			assert(johnMemb.start.isEmpty)
			assert(johnMemb.stop.isEmpty)
			assert(janeMemb.start.isEmpty)
			assert(janeMemb.stop.isEmpty)
		}

	}

	describe("CP takes over a description of a PI person"){

		Given("starting with a single station with single PI and no own CP statements")

		val state = init(Nil, _ => Nil)
		val initSnap = atcInitSnap(jane)

		state.tcServer.applyAll(state.calc.calcDiff(initSnap).result.get)()

		When("CP creates a new person metadata and associates it with the exising TC person metadata")

		val cpJane = TcPerson[A](UriId("Jane_CP"), jane.tcIdOpt, "Jane", "CP", Some("jane.cp@icos-cp.eu"), None)
		state.cpServer.addAll(state.maker.getStatements(cpJane))

		it("Then arrival of an unchanged TC metadata snapshot results in deletion of TC's own statements"){

			val updates = state.calc.calcDiff(initSnap).result.get.toIndexedSeq //no change in the TC picture
			state.tcServer.applyAll(updates)()

			val personStats = state.maker.getStatements(jane)
			val deleted = updates.filter(!_.isAssertion).map(_.statement).toSet
			assert(personStats.forall(deleted.contains))
		}

		it("And subsequent arrival of an unchanged TC metadata has no further effect"){
			val updates2 = state.calc.calcDiff(initSnap).result.get.toIndexedSeq
			assert(updates2.isEmpty)
		}
	}

	describe("CP takes over a description of an organization (not station) where a person has a role"){

		Given("starting with a single org with a single researcher and no own CP statements")

		val uniOrg = Organization(UriResource(null, Some("uni"), Nil), "Just Some Uni", None, None, None)
		val uni = TcGenericOrg(UriId("uni"), Some(aId("uni0")), uniOrg)
		val janeAtUni = Membership[A](UriId(""), new AssumedRole[A](Researcher, jane, uni, None, None), None, None)
		val initSnap = new TcState[A](Nil, Seq(janeAtUni), Nil)
		val state = init(Nil, _ => Nil)
		state.tcServer.applyAll(state.calc.calcDiff(initSnap).result.get)()

		When("CP creates a new org metadata and associates it with the exising TC org metadata")

		val cpUniOrg = Organization(UriResource(null, Some("uni proper"), Nil), "Properly named Uni", None, None, None)
		val cpUni = TcGenericOrg(UriId("cpuni"), Some(aId("uni0")), cpUniOrg)
		state.cpServer.addAll(state.maker.getStatements(cpUni))

		it("Unchanged TC metadata snapshot results in deletion of TC's own org and in membership using the CP one instead"){
			val updates = state.calc.calcDiff(initSnap).result.get.toIndexedSeq //no change in the TC picture
			state.tcServer.applyAll(updates)()
			val tcUri = state.maker.getIri(uni)
			val cpUri = state.maker.getIri(cpUni)
			assert(updates.filter(_.statement.getSubject == tcUri).forall(_.isAssertion == false))

			val meta = new CpmetaVocab(state.cpServer.factory)
			val atOrgStats = updates.filter(_.statement.getPredicate == meta.atOrganization)
				.map(u => u.statement.getObject -> u.isAssertion)
				.toMap
			assert(atOrgStats === Map(tcUri -> false, cpUri -> true))
		}

		it("And subsequent arrival of an unchanged TC metadata has no further effect"){
			val updates2 = state.calc.calcDiff(initSnap).result.get.toIndexedSeq
			assert(updates2.isEmpty)
		}
	}

	describe("TC adds ORCID id info to a person that CP took responsibility for"):
		Given("starting with a single org with single researcher without ORCID, described by both CP and TC")
		val initSnap = atcInitSnap(jane)
		val state = init(initSnap :: Nil, _.getStatements(jane))
		When("a no-change metadata update comes")
		val diffV = state.calc.calcDiff(initSnap)
		val initUpdates = diffV.result.get
		state.tcServer.applyAll(initUpdates)()
		it("erases all the duplicate statements about the researcher from the TC/ICOS RDF graph"):
			assert(diffV.errors.isEmpty)
			assert(initUpdates.length === 5)
			assert(initUpdates.forall(_.isAssertion == false))

		When("a new TC metadata snapshot comes, where the researcher got an ORCID id")
		val orcidStr = "0000-0002-4742-958X"
		val janeWithOrcid = jane.copy(orcid = Orcid.unapply(orcidStr))
		val snapWithOrcid = atcInitSnap(janeWithOrcid)

		it("results in a single-triple ORCID info update"):
			val updates = state.calc.calcDiff(snapWithOrcid).result.get.toIndexedSeq
			assert(updates.size === 1)
			val theUpdate = updates.head
			assert(theUpdate.isAssertion)
			assert(theUpdate.statement.getObject.stringValue.endsWith(orcidStr))


	def init(initTcState: Seq[TcState[_ <: TC]], cpOwn: RdfMaker => Seq[Statement]): TestState = {
		val repo = Loading.emptyInMemory
		given factory: ValueFactory = repo.getValueFactory
		val vocab = new CpVocab(factory)
		val meta = new CpmetaVocab(factory)
		val rdfMaker = RdfMaker(vocab, meta)(using Envri.ICOS)

		val tcGraphUri = new URI("http://test.icos.eu/tcState")
		val cpGraphUri = new URI("http://test.icos.eu/cpOwnMetaInstances")
		val tcGraphIri = tcGraphUri.toRdf
		val cpGraphIri = cpGraphUri.toRdf
		val tcServer = new Rdf4jInstanceServer(repo, Seq(tcGraphIri, cpGraphIri), tcGraphIri)
		val cpServer = new Rdf4jInstanceServer(repo, cpGraphIri)

		val cpOwnStats = cpOwn(rdfMaker)
		cpServer.addAll(cpOwnStats)

		tcServer.addAll(initTcState.flatMap(getStatements(rdfMaker, _)))
		val metaReader = new DobjMetaReader(vocab):
			val metaVocab = meta
		val lenses = MetaflowLenses(
			cpLens = RdfLens.cpLens(cpGraphUri, Seq(cpGraphUri)),
			envriLens = RdfLens.metaLens(tcGraphUri, Seq(cpGraphUri, tcGraphUri)),
			docLens = RdfLens.docLens(cpGraphUri, Seq.empty)
		)
		val rdfReader = new RdfReader(metaReader, tcServer, lenses)
		new TestState(new RdfDiffCalc(rdfMaker, rdfReader), rdfReader, rdfMaker, tcServer, cpServer)
	}

	def getStatements[T <: TC](rdfMaker: RdfMaker, state: TcState[T]): Seq[Statement] =
		given TcConf[T] = state.tcConf
		state.stations.flatMap(rdfMaker.getStatements) ++
		state.roles.flatMap(rdfMaker.getStatements) ++
		state.roles.map(_.role.holder).flatMap(rdfMaker.getStatements) ++
		state.instruments.flatMap(rdfMaker.getStatements)

end RdfDiffCalcTests

object RdfDiffCalcTests:
	class TestState(
		val calc: RdfDiffCalc, val reader: RdfReader, val maker: RdfMaker,
		val tcServer: InstanceServer, val cpServer: InstanceServer
	)
