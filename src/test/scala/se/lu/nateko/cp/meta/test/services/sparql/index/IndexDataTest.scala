package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Value}
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

class IndexDataTest extends AnyFunSpec {
	describe("processTriple") {
		it("clears fName of ObjEntry when hasName tuple is deleted") {
			val repo = Loading.emptyInMemory
			val server = new Rdf4jInstanceServer(repo)
			val factory = repo.getValueFactory
			val vocab = CpmetaVocab(factory)
			val data = IndexData(100)()

			given TriplestoreConnection = server.getConnection()

			val insert = data.processTriple(_, _, _, true, vocab)

			insert(factory.createIRI("test:subject"), vocab.hasName, factory.createIRI("test:name"))
		}
	}
}
