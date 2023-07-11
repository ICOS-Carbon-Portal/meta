package se.lu.nateko.cp.meta.test.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.services.upload.ObjMetadataUpdater
import se.lu.nateko.cp.meta.utils.rdf4j.tripleToStatement
import se.lu.nateko.cp.meta.utils.rdf4j.createStringLiteral

import java.net.URI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.RDF

import scala.concurrent.ExecutionContext.Implicits.global

class MetadataUpdaterTests extends AsyncFunSpec:
	class Setup(
		val cpVocab: CpVocab,
		val metaVocab: CpmetaVocab,
		val updater: ObjMetadataUpdater,
		val server: InstanceServer,
		val factory: ValueFactory
	):
		def hash(base64Url: String) = Sha256Sum.fromBase64Url(base64Url).get
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
		val updater = ObjMetadataUpdater(cpVocab, metaVocab, sparqler)
		val server = Rdf4jInstanceServer(repo, "http://meta.icos-cp.eu/tests/metaupdater/")
		Setup(cpVocab, metaVocab, updater, server, factory)

	describe("getCurrentStatements"):
		describe("one object being collectively deprecated by two"):
			val setup = initTestSetup
			import setup.*
			val oldHash = hash("old_vJN69j6rRPKxTbJZckEa")
			val oldObj = cpVocab.getStaticObject(oldHash)
			val coll = cpVocab.getNextVersionColl(oldHash)
			val Seq(newHash1, newHash2) = Seq("new1_JN69j6rRPKxTbJZckEa", "new2_JN69j6rRPKxTbJZckEa").map(hash)
			val Seq(newObj1, newObj2) = Seq(newHash1, newHash2).map(cpVocab.getStaticObject)

			val initTriples: Seq[(IRI, IRI, Value)] = Seq(
				(oldObj, metaVocab.hasName, stringLit("old_obj_file_name.txt")),
				(coll, RDF.TYPE, metaVocab.plainCollectionClass),
				(coll, metaVocab.isNextVersionOf, oldObj),
				(coll, metaVocab.dcterms.hasPart, newObj1),
				(coll, metaVocab.dcterms.hasPart, newObj2),
				(newObj1, metaVocab.hasName, stringLit("new_obj_1_file_name.txt")),
				(newObj2, metaVocab.hasName, stringLit("new_obj_2_file_name.txt")),
			)
			server.addAll(initTriples.map(factory.tripleToStatement))

			it("each of the new objects is 'responsible' for its own props and membership in the deprecating collection"):
				updater.getCurrentStatements(newHash2, server).map: stats =>
					stats.foreach(println)
					assert(stats.size === 2) //file name plus membership

end MetadataUpdaterTests