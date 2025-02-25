package se.lu.nateko.cp.meta.test.services.upload

import eu.icoscp.envri.Envri
import java.net.URI
import java.time.Instant
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Literal, Value, ValueFactory}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.GivenWhenThen
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, Rdf4jInstanceServer}
import se.lu.nateko.cp.meta.services.upload.ObjMetadataUpdater
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.createStringLiteral

class MetadataUpdaterTests extends AsyncFunSpec with GivenWhenThen:
	class Setup(
		val cpVocab: CpVocab,
		val metaVocab: CpmetaVocab,
		val updater: ObjMetadataUpdater,
		val server: InstanceServer,
		val factory: ValueFactory
	):
		def hash(base64Url: String): Sha256Sum = Sha256Sum.fromBase64Url(base64Url).get
		def stringLit(str: String): Literal = factory.createStringLiteral(str)

	given Envri = Envri.ICOS
	given EnvriConfigs = Map(
		Envri.ICOS -> EnvriConfig(
			authHost = "cpauth.icos-cp.eu",
			dataHost = "data.icos-cp.eu",
			metaHost = "meta.icos-cp.eu",
			dataItemPrefix = URI("https://meta.icos-cp.eu/tests/"),
			metaItemPrefix = URI("https://meta.icos-cp.eu/tests/"),
			defaultTimezoneId = "UTC"
		)
	)

	def initTestSetup: Setup =
		val repo = new SailRepository(new MemoryStore)
		val factory = repo.getValueFactory
		val cpVocab = CpVocab(factory)
		val metaVocab = CpmetaVocab(factory)
		val sparqler = Rdf4jSparqlRunner(repo)
		val updater = ObjMetadataUpdater(cpVocab, metaVocab)
		val server = Rdf4jInstanceServer(repo, "http://meta.icos-cp.eu/tests/metaupdater/")
		Setup(cpVocab, metaVocab, updater, server, factory)

	def getProvenanceTriples(objHash: Sha256Sum, obj: IRI, setup: Setup): Seq[(IRI, IRI, Value)] =
		val submissionUri = setup.cpVocab.getSubmission(objHash)
		val submittingOrg = "http://meta.icos-cp.eu/resources/organizations/CP"

		Seq(
			(submissionUri, RDF.TYPE, setup.metaVocab.submissionClass),
			(submissionUri, setup.metaVocab.prov.startedAtTime, setup.cpVocab.lit(Instant.now)),
			(submissionUri, setup.metaVocab.prov.wasAssociatedWith, setup.factory.createIRI(submittingOrg)),
			(obj, setup.metaVocab.wasSubmittedBy, submissionUri)
		)

	def getObjTriples(objHash: Sha256Sum, fileName: String, setup: Setup, withProvenance: Boolean): Seq[(IRI, IRI, Value)] =
		val metaVocab = setup.metaVocab
		val cpVocab = setup.cpVocab
		val obj = setup.cpVocab.getStaticObject(objHash)

		Seq((obj, metaVocab.hasName, setup.stringLit("old_obj_file_name.txt"))) ++
		(if withProvenance then getProvenanceTriples(objHash, obj, setup) else Nil)

	describe("getCurrentStatements"):
		describe("one object being collectively deprecated by two"):
			val setup = initTestSetup
			import setup.*
			val oldHash = hash("old_vJN69j6rRPKxTbJZckEa")
			val oldObj = cpVocab.getStaticObject(oldHash)
			val coll = cpVocab.getNextVersionColl(oldHash)
			val Seq(newHash1, newHash2) = Seq("new1_JN69j6rRPKxTbJZckEa", "new2_JN69j6rRPKxTbJZckEa").map(hash)

			val initTriples: Seq[(IRI, IRI, Value)] =
				getObjTriples(oldHash, "old_obj_file_name.txt", setup, false) ++
				getObjTriples(newHash1, "new_obj_1_file_name.txt", setup, false) ++
				getObjTriples(newHash2, "new_obj_2_file_name.txt", setup, false) ++
				Seq(
					(coll, RDF.TYPE, metaVocab.plainCollectionClass),
					(coll, metaVocab.isNextVersionOf, oldObj),
					(coll, metaVocab.dcterms.hasPart, cpVocab.getStaticObject(newHash1)),
					(coll, metaVocab.dcterms.hasPart, cpVocab.getStaticObject(newHash2)),
				)

			Given("two objects deprecating a third one through a plain collection")
			server.addAll(initTriples.map(factory.createStatement.tupled))

			it("each of the new objects is 'responsible' for its own props and membership in the deprecating collection"):
				server.access:
					val stats = updater.getCurrentStatements(newHash2)
					//stats.foreach(println)
					assert(stats.size === 2) //file name plus membership
			When("one of the objects is excluded from the deprecating collection")
			it("the other object 'assumes responsibility' over the collection's RDF statements"):
				server.remove(factory.createStatement(coll, metaVocab.dcterms.hasPart, cpVocab.getStaticObject(newHash1))) 
				server.access:
					val stats = updater.getCurrentStatements(newHash2)
					assert(stats.size === 4)

		describe("one object with provenance being deprecated by another"):
			val setup = initTestSetup
			import setup.*
			val objHash = hash("old_vJN69j6rRPKxTbJZckEa")
			val newObjHash = hash("new1_JN69j6rRPKxTbJZckEa")

			val initTriples: Seq[(IRI, IRI, Value)] =
				getObjTriples(objHash, "obj_1_file_name.txt", setup, withProvenance = true)
			
			val newVTriples: Seq[(IRI, IRI, Value)] =
				getObjTriples(newObjHash, "new_1_file_name.txt", setup, withProvenance = true)

			Given("single object with submission provenance")
			server.addAll(initTriples.map(factory.createStatement.tupled))

			it("returns own object props and submission provenance"):
				server.access:
					val stats = updater.getCurrentStatements(objHash)
					assert(stats.size === 5) //file name, submission provenance triples, acquisition provenance triples

			When("new version of the object is added, and the new object is queried")
			it("returns triples with deprecation link but without old object's statements"): 
				server.addAll(newVTriples.map(factory.createStatement.tupled))
				server.add(factory.createStatement(cpVocab.getStaticObject(newObjHash), metaVocab.isNextVersionOf, cpVocab.getStaticObject(objHash)))
				server.access:
					val stats = updater.getCurrentStatements(newObjHash)
					assert(stats.size === 6) //file name, submission provenance triples, acquisition provenance triples

end MetadataUpdaterTests
