package se.lu.nateko.cp.meta.test.icos

import java.net.URI

import org.eclipse.rdf4j.model.Statement
import org.scalatest.FunSpec

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.icos._
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import org.scalatest.GivenWhenThen
import se.lu.nateko.cp.meta.instanceserver.InstanceServer


class RdfDiffCalcTests extends FunSpec with GivenWhenThen{

	implicit val envriConfs = Map(
		Envri.ICOS -> EnvriConfig(null, null, null, new URI("http://test.icos.eu/resources/"))
	)

	describe("person name change"){

		Given("starting with an empty state with no CP own statements")

		val (calc, reader, tcServer) = init(Nil, _ => Nil)

		When("an ATC-state snapshot with single station is inserted")

		type A = ATC.type
		import TcConf.AtcConf.makeId

		val jane = Person[A]("Jane_Doe", makeId("pers_0"), "Jane", "Doe", Some("jane.doe@icos-ri.eu"))
		val cpStation = CpMobileStation[A]("AIR1", makeId("43"), "Airplane 1", "AIR1", None)
		val theStation = new TcStation[A](cpStation, OneOrMorePis(jane))

		val snap = new TcState[A](stations = Seq(theStation), roles = Nil, instruments = Nil)

		val initUpdates = calc.calcDiff(snap).toIndexedSeq
		Then("")
		it("it results in expected sequence of RDF updates"){
			assert(initUpdates.forall(_.isAssertion))
			assert(initUpdates.size > 10)
			//initUpdates.foreach(println)
		}

		tcServer.applyAll(initUpdates)

		And("reading current TC state back produces expected value")

		it("(has the PI, the station and the role)"){
			val s = reader.getCurrentState[A]
			assert(s.stations.size === 1)
			assert(s.stations.head === cpStation)
			assert(s.instruments.isEmpty)
			assert(s.roles.size === 1)
			val memb = s.roles.head
			assert(memb.start.isEmpty) //init state was empty, so cannot know when the role was assumed first
			assert(memb.stop.isEmpty) //just created, so cannot have ended
			assert(memb.role.role === PI)
			assert(memb.role.org === cpStation)
			assert(memb.role.holder === jane)
		}

		When("afterwards new snapshot comes with person last name changed")

		val jane2 = jane.copy(cpId = "Jane_Smith", lName = "Smith")
		val theStation2 = new TcStation[A](cpStation, OneOrMorePis(jane2))
		val snap2 = new TcState[A](stations = Seq(theStation2), roles = Nil, instruments = Nil)

		val nameUpdates = calc.calcDiff(snap2).toIndexedSeq
		Then("")
		it("only name-changing updates are applied"){
			assert(nameUpdates.size === 2)
			nameUpdates foreach println
		}
	}

	def init(initTcState: Seq[CpTcState[_ <: TC]], cpOwn: RdfMaker => Seq[Statement]): (RdfDiffCalc, RdfReader, InstanceServer) = {
		val repo = Loading.emptyInMemory
		val factory = repo.getValueFactory
		val vocab = new CpVocab(factory)
		val meta = new CpmetaVocab(factory)
		val rdfMaker = new RdfMaker(vocab, meta)

		val tcGraphUri = factory.createIRI("http://test.icos.eu/tcState")
		val cpGraphUri = factory.createIRI("http://test.icos.eu/cpOwnMetaInstances")
		val tcServer = new Rdf4jInstanceServer(repo, tcGraphUri)
		val cpServer = new Rdf4jInstanceServer(repo, cpGraphUri)

		cpServer.addAll(cpOwn(rdfMaker))

		tcServer.addAll(initTcState.flatMap(getStatements(rdfMaker, _)))
		val rdfReader = new RdfReader(cpServer, tcServer)
		(new RdfDiffCalc(rdfMaker, rdfReader), rdfReader, tcServer)
	}

	def getStatements[T <: TC](rdfMaker: RdfMaker, state: CpTcState[T]): Seq[Statement] = {
		implicit val tcConf = state.tcConf
		state.stations.flatMap(rdfMaker.getStatements[T]) ++
		state.roles.flatMap(rdfMaker.getStatements[T]) ++
		state.instruments.flatMap(rdfMaker.getStatements[T])
	}

}
