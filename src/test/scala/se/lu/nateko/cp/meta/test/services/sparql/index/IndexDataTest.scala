package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.IRI
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.utils.rdf4j.Loading
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.CpVocab

class IndexDataTest extends AnyFunSpec {
	describe("processTriple") {
		it("clears fName of ObjEntry when hasName tuple is deleted") {
			val repo = Loading.emptyInMemory
			val server = new Rdf4jInstanceServer(repo)
			val factory = repo.getValueFactory
			val vocab = CpmetaVocab(factory)

			val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")

			// Make sure we insert a DataObject
			val hash : Sha256Sum =
				subject match {
					case CpVocab.DataObject(hash, _prefix) => hash
				}

			val data = IndexData(100)()
			server.access {
				// Insert hasName triple
				data.processTriple(subject, vocab.hasName, factory.createIRI("test:name"), true, vocab)
				assert(data.objs.length == 1)
				assert(data.getObjEntry(hash).fileName == Some("test:name"))
				// Remove it
				data.processTriple(subject, vocab.hasName, factory.createIRI("test:name"), false, vocab)
				assert(data.getObjEntry(hash).fileName == None)
			}
		}
	}
}
