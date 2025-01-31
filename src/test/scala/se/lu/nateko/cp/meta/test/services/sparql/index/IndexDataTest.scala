package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Value}
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

class IndexDataTest extends AnyFunSpec {
	describe("processTriple") {
		it("clears fName of ObjEntry when hasName tuple is deleted") {
			val repo = Loading.emptyInMemory
			val server = new Rdf4jInstanceServer(repo)
			val factory = repo.getValueFactory
			val vocab = CpmetaVocab(factory)

			val hash: Sha256Sum = Sha256Sum.fromString("AAAAAAAAAAAAAAAAAAAAAAAA").get
			info(hash.toString())

			// server.access:
			// 	val hash = TriplestoreConnection.getHashsum(subject, vocab.hasSha256sum)
			// 	info("hash: "+hash.result.get.toString())


			val subject = factory.createIRI("test:subject")
			server.add(factory.createStatement(subject, vocab.hasName, factory.createIRI("test:name")))
			assert(server.getStatements(Some(subject), None, None).length == 1)

			val data = IndexData(100)()
			server.access {
				val insert = data.processTriple(_, _, _, true, vocab)
				// Ignored?
				insert(subject, vocab.hasName, factory.createIRI("test:name"))

				assert(data.objs.length == 0)
				val entry = data.getObjEntry(hash)
				assert(data.objs.length == 1)
				val entry2 = data.getObjEntry(hash)
				assert(data.objs.length == 1)
				assert(entry == entry2)

				info(data.objs.toString())

				// insert(subject, vocab.hasSha256sum, factory.createIRI(hash.toString()))
				// info(hash.toString())

				// val hash = TriplestoreConnection.getHashsum(subject, vocab.hasSha256sum)
				// info(entry.fName)
			}

		}
	}
}
