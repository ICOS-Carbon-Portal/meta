package se.lu.nateko.cp.meta.test.services.sync

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Value}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sync.{RemoteMetaSync, SyncKind, SyncProgress}
import se.lu.nateko.cp.meta.services.{CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.{accessEagerly, transact}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class RemoteMetaSyncTest extends AnyFunSpec with BeforeAndAfterAll:

	private given system: ActorSystem = ActorSystem("RemoteMetaSyncTest")
	override def afterAll(): Unit = system.terminate()

	private val meta = CpmetaVocab(org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance)
	private val f = meta.factory
	private def res(local: String) = f.createIRI("http://meta.icos-cp.eu/resources/" + local)

	private val objGraph = res("atmcsv/")
	private val cpGraph = res("cpmeta/")
	private val icosGraph = res("icos/")
	private val obj = res("obj")
	private val unknownObj = res("unknownObj")
	private val acq = res("acq_obj")
	private val prod = res("prod_obj")
	private val contribs = res("prod_contribs_obj")
	private val spec = res("cpmeta/spec")
	private val person = res("people/Some_Person")
	private val memb = res("memberships/memb")
	private val station = res("stations/ST")

	type Quad = (IRI, Value, Value, IRI)

	private def repoWith(quads: Quad*): Repository =
		val repo = SailRepository(MemoryStore())
		repo.init()
		repo.transact(conn => quads.foreach((s, p, o, g) => conn.add(s, p.asInstanceOf[IRI], o, g))).get
		repo

	private def has(repo: Repository, quad: Quad): Boolean =
		val (s, p, o, g) = quad
		repo.accessEagerly(_.hasStatement(s, p.asInstanceOf[IRI], o, false, g))

	private val objType: Quad = (obj, RDF.TYPE, meta.dataObjectClass, objGraph)
	private val newName: Quad = (obj, meta.hasName, f.createLiteral("new.csv"), objGraph)
	private val oldName: Quad = (obj, meta.hasName, f.createLiteral("old.csv"), objGraph)
	private val acqLink: Quad = (obj, meta.wasAcquiredBy, acq, objGraph)
	private val staleAcqTime: Quad = (acq, meta.prov.startedAtTime, f.createLiteral("2020-01-01"), objGraph)
	private val acqStation: Quad = (acq, meta.prov.wasAssociatedWith, station, objGraph)
	private val prodLink: Quad = (obj, meta.wasProducedBy, prod, objGraph)
	private val contribsLink: Quad = (prod, meta.wasParticipatedInBy, contribs, objGraph)
	private val firstContrib: Quad = (contribs, f.createIRI(RDF.NAMESPACE + "_1"), person, objGraph)
	private val specLink: Quad = (obj, meta.hasObjectSpec, spec, objGraph)
	private val licence: Quad = (obj, meta.dcterms.license, f.createIRI("https://creativecommons.org/licenses/by/4.0"), objGraph)
	private val specName: Quad = (spec, meta.hasName, f.createLiteral("Spec"), cpGraph)
	private val stationName: Quad = (station, meta.hasName, f.createLiteral("Station"), icosGraph)
	private val unknownObjType: Quad = (unknownObj, RDF.TYPE, meta.dataObjectClass, objGraph)
	private val personType: Quad = (person, RDF.TYPE, meta.personClass, icosGraph)
	private val personName: Quad = (person, meta.hasFirstName, f.createLiteral("Some"), icosGraph)
	private val membLink: Quad = (person, meta.hasMembership, memb, icosGraph)
	private val membRole: Quad = (memb, meta.hasRole, res("roles/PI"), icosGraph)

	private val remote = repoWith(
		objType, newName, licence, acqLink, acqStation, prodLink, contribsLink, firstContrib, specLink, specName,
		stationName, unknownObjType, personType, personName, membLink, membRole
	)

	private def sync(prune: Boolean): (Repository, Seq[SyncProgress]) =
		val local = repoWith(objType, oldName, acqLink, staleAcqTime, personType)
		val progress = RemoteMetaSync(local, Rdf4jSparqlRunner(remote), prune)
			.run(Seq(SyncKind.DataObjects, SyncKind.People))
			.runWith(Sink.seq)
		local -> Await.result(progress, 10.seconds)

	describe("RemoteMetaSync"):

		it("adds the remote statements associated with locally known resources"):
			val (local, _) = sync(prune = false)
			Seq(newName, licence, acqStation, prodLink, contribsLink, firstContrib, specLink, personName, membLink, membRole)
				.foreach(q => assert(has(local, q), q))

		it("does not add statements of independent resources or of unknown objects"):
			val (local, _) = sync(prune = false)
			Seq(specName, stationName, unknownObjType).foreach(q => assert(!has(local, q), q))

		it("keeps local-only statements unless pruning"):
			val (local, _) = sync(prune = false)
			assert(has(local, oldName))
			assert(has(local, staleAcqTime))

		it("removes outdated statements about remotely reported subjects when pruning"):
			val (local, progress) = sync(prune = true)
			assert(!has(local, oldName))
			assert(!has(local, staleAcqTime))
			assert(has(local, newName))
			assert(progress.exists(_.toString == "People: 1 roots in 1 batches, 0 missing remotely, 3 statements added, 0 statements removed"))

		it("reports the progress of every kind"):
			val (_, progress) = sync(prune = true)
			assert(progress.map(_.toString).exists(_.startsWith("DataObjects: 1 roots in 1 batches, 0 missing remotely, 7 statements added, 2 statements removed")))
