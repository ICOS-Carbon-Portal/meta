package se.lu.nateko.cp.meta.test.services.sparql.index

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.sparql.magic.index.{IndexData, IndexUpdate}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
/*
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jIterationIterator
 */

class IndexDataTest extends AnyFunSuite {
	test("ObjEntry.fileName is cleared when hasName statement is deleted") {
		val factory = SimpleValueFactory.getInstance()
		val vocab = CpmetaVocab(factory)
		val subject: IRI = factory.createIRI("https://meta.icos-cp.eu/objects/oAzNtfjXddcnG_irI8fJT7W6")
		val CpVocab.DataObject(hash, _) = subject: @unchecked
		val data = IndexData(100)()

		// IndexData requires a StatementSource but in this case we never pull any statements,
		// hence we can leave things unimplemented.

		val statement = Rdf4jStatement(subject, vocab.hasName, factory.createLiteral("test name"))

		given StatementSource = StaticStatements(Seq())

		// Insert hasName triple
		data.processUpdate(IndexUpdate(statement, true), vocab)
		assert(data.objs.length == 1)
		assert(data.getObjEntry(hash).fileName === Some("test name"))

		// Remove it
		data.processUpdate(IndexUpdate(statement, false), vocab)
		assert(data.getObjEntry(hash).fileName === None)
		assert(data.objs.length == 1)
	}

	class StaticStatements(statements: Seq[Statement]) extends StatementSource {
		def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] = {
			CloseableIterator.Wrap(statements.iterator, () => ())
		}
		def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = ??? // Leave unimplemented until used
	}
}
