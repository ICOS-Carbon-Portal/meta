package se.lu.nateko.cp.meta.test.icos

import java.net.URI

import org.eclipse.rdf4j.model.Statement
import org.scalatest.funspec.AnyFunSpec

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.icos._
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import org.scalatest.GivenWhenThen
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import RdfDiffCalcTests._

class RdfDiffCalcTests extends AnyFunSpec with GivenWhenThen{

	implicit val envriConfs = Map(
		Envri.ICOS -> EnvriConfig(null, null, null, null, new URI("http://test.icos.eu/resources/"))
	)

	type A = ATC.type
	import TcConf.AtcConf.{makeId => aId}

	val jane = Person[A](UriId("Jane_Doe"), Some(aId("pers_0")), "Jane", "Doe", Some("jane.doe@icos-ri.eu"))
	val CountryCode(se) = "SE"
	val airCpStation = TcMobileStation[A](UriId("AIR1"), aId("43"), "Airplane 1", "AIR1", Some(se), None)

	def atcInitSnap(pi: Person[A]): TcState[A] = {
		val piMemb = Membership[A](UriId(""), new AssumedRole(PI, pi, airCpStation, None), None, None)
		new TcState[A](stations = Seq(airCpStation), roles = Seq(piMemb), instruments = Nil)
	}

	describe("person name change"){

		Given("starting with an empty state with no own CP statements")

		val state = init(Nil, _ => Nil)

		When("an ATC-state snapshot with single station is inserted")

		val initUpdates = state.calc.calcDiff(atcInitSnap(jane)).result.get.toIndexedSeq

		it("Then it results in expected sequence of RDF updates"){
			assert(initUpdates.forall(_.isAssertion))
			assert(initUpdates.size >= 13) //the number may change if metadata model changes
		}

		state.tcServer.applyAll(initUpdates)

		And("reading current TC state back produces expected value")

		it("(has the expected PI, the station and the role)"){
			val s = state.reader.getCurrentState[A].result.get
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

	describe("PI change"){

		Given("starting with a single station with single PI and no own CP statements")

		val state = init(Nil, _ => Nil)
		state.tcServer.applyAll(state.calc.calcDiff(atcInitSnap(jane)).result.get)

		When("a new snapshot comes where the PI has changed")

		val john = Person[A](UriId("John_Brown"), Some(aId("pers_1")), "John", "Brown", Some("john.brown@icos-ri.eu"))
		val piUpdates = state.calc.calcDiff(atcInitSnap(john)).result.get.toIndexedSeq
		state.tcServer.applyAll(piUpdates)

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

		state.tcServer.applyAll(state.calc.calcDiff(initSnap).result.get)

		When("CP creates a new person metadata and associates it with the exising TC person metadata")

		val cpJane = Person[A](UriId("Jane_CP"), jane.tcIdOpt, "Jane", "CP", Some("jane.cp@icos-cp.eu"))
		state.cpServer.addAll(state.maker.getStatements(cpJane))

		it("Then arrival of an unchanged TC metadata snapshot results in deletion of TC's own statements"){

			val updates = state.calc.calcDiff(initSnap).result.get.toIndexedSeq //no change in the TC picture
			state.tcServer.applyAll(updates)

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

		val uni = CompanyOrInstitution(UriId("uni"), Some(aId("uni0")), "Just Some Uni", None)
		val janeAtUni = Membership[A](UriId(""), new AssumedRole[A](Researcher, jane, uni, None), None, None)
		val initSnap = new TcState[A](Nil, Seq(janeAtUni), Nil)
		val state = init(Nil, _ => Nil)
		state.tcServer.applyAll(state.calc.calcDiff(initSnap).result.get)

		When("CP creates a new org metadata and associates it with the exising TC org metadata")

		val cpUni = CompanyOrInstitution(UriId("cpuni"), Some(aId("uni0")), "Properly named Uni", None)
		state.cpServer.addAll(state.maker.getStatements(cpUni))

		it("Unchanged TC metadata snapshot results in deletion of TC's own org and in membership using the CP one instead"){
			val updates = state.calc.calcDiff(initSnap).result.get.toIndexedSeq //no change in the TC picture
			state.tcServer.applyAll(updates)
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

	def init(initTcState: Seq[TcState[_ <: TC]], cpOwn: RdfMaker => Seq[Statement]): TestState = {
		val repo = Loading.emptyInMemory
		val factory = repo.getValueFactory
		val vocab = new CpVocab(factory)
		val meta = new CpmetaVocab(factory)
		val rdfMaker = new RdfMaker(vocab, meta)

		val tcGraphUri = factory.createIRI("http://test.icos.eu/tcState")
		val cpGraphUri = factory.createIRI("http://test.icos.eu/cpOwnMetaInstances")
		val tcServer = new Rdf4jInstanceServer(repo, Seq(tcGraphUri, cpGraphUri), Seq(tcGraphUri))
		val cpServer = new Rdf4jInstanceServer(repo, cpGraphUri)

		cpServer.addAll(cpOwn(rdfMaker))

		tcServer.addAll(initTcState.flatMap(getStatements(rdfMaker, _)))
		val rdfReader = new RdfReader(cpServer, tcServer)
		new TestState(new RdfDiffCalc(rdfMaker, rdfReader), rdfReader, rdfMaker, tcServer, cpServer)
	}

	def getStatements[T <: TC](rdfMaker: RdfMaker, state: TcState[T]): Seq[Statement] = {
		implicit val tcConf = state.tcConf
		state.stations.flatMap(rdfMaker.getStatements[T]) ++
		state.roles.flatMap(rdfMaker.getStatements[T]) ++
		state.instruments.flatMap(rdfMaker.getStatements[T])
	}

}

object RdfDiffCalcTests{
	class TestState(
		val calc: RdfDiffCalc, val reader: RdfReader, val maker: RdfMaker,
		val tcServer: InstanceServer, val cpServer: InstanceServer
	)
}
