package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.sparql.magic.index.{IndexData, TripleStatement}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}


class IndexDataTest extends AnyFunSpec {
	describe("processTriple") {
		it("clears fileName of ObjEntry when hasName tuple is deleted") {
			val factory = SimpleValueFactory.getInstance()
			val vocab = CpmetaVocab(factory)
			val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")
			val CpVocab.DataObject(hash, _) = subject : @unchecked
			val data = IndexData(100)()

			// IndexData requires a StatementSource but in this case we never pull any statements,
			// hence we can leave things unimplemented.
			given StatementSource with
				def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] = ???
				def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = ???

			// Insert hasName triple
			data.processTriple(TripleStatement(subject, vocab.hasName, factory.createLiteral("test name"), true), vocab)
			assert(data.objs.length == 1)
			assert(data.getObjEntry(hash).fileName === Some("test name"))

			// Remove it
			data.processTriple(TripleStatement(subject, vocab.hasName, factory.createLiteral("test name"), false), vocab)
			assert(data.getObjEntry(hash).fileName === None)
			assert(data.objs.length == 1)
		}
	}
}
