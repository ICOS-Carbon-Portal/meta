package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.sparql.magic.index.{IndexData, TripleStatement}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement

class IndexDataTest extends AnyFunSuite {
	test("ObjEntry.fileName is cleared when hasName statement is deleted") {
		val factory = SimpleValueFactory.getInstance()
		val vocab = CpmetaVocab(factory)
		val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")
		val CpVocab.DataObject(hash, _) = subject: @unchecked
		val data = IndexData(100)()

		// IndexData requires a StatementSource but in this case we never pull any statements,
		// hence we can leave things unimplemented.
		given StatementSource with
			def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] = ???
			def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = ???

		val statement = Rdf4jStatement(subject, vocab.hasName, factory.createLiteral("test name"))

		// Insert hasName triple
		data.processUpdate(statement, true, vocab)
		assert(data.objs.length == 1)
		assert(data.getObjEntry(hash).fileName === Some("test name"))

		// Remove it
		data.processUpdate(statement, false, vocab)
		assert(data.getObjEntry(hash).fileName === None)
		assert(data.objs.length == 1)
	}
}
