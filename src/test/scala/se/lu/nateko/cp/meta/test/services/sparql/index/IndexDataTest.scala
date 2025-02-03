package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.services.sparql.magic.index.IndexData
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

// IndexData requires a TriplestoreConnection but in current tests it is not actually used.
private class DummyTSC extends TriplestoreConnection {
  override def close(): Unit = ???
  override def primaryContext: IRI = ???
  override def readContexts: Seq[IRI] = ???
  override def factory: ValueFactory = ???
  override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] = ???
  override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = ???
  override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection = ???
}

class IndexDataTest extends AnyFunSpec {
	describe("processTriple") {
		it("clears fName of ObjEntry when hasName tuple is deleted") {
			val repo = Loading.emptyInMemory
			val factory = repo.getValueFactory
			val vocab = CpmetaVocab(factory)

			val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")

			// Make sure we insert a DataObject
			val hash: Sha256Sum =
				subject match {
					case CpVocab.DataObject(hash, _prefix) => hash
				}

			val data = IndexData(100)()
			given TriplestoreConnection = DummyTSC()

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
