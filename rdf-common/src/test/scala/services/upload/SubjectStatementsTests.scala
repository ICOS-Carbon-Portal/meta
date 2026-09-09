package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Value}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, StatementSource}
import se.lu.nateko.cp.meta.services.CpmetaVocab

class SubjectStatementsTests extends AnyFunSpec:
	private val factory = SimpleValueFactory.getInstance()
	private val subject = factory.createIRI("https://example.org/person")
	private val vocab = CpmetaVocab(factory)

	private class Source(var statements: IndexedSeq[RdfStatement]) extends StatementSource:
		var reads = 0
		var closes = 0
		def getStatements(s: IRI | Null, p: IRI | Null, o: Value | Null): CloseableIterator[RdfStatement] =
			reads += 1
			new CloseableIterator.Wrap(statements.iterator.filter(st =>
				(s == null || st.subject == s) && (p == null || st.predicate == p) && (o == null || st.obj == o)
			), () => closes += 1)
		def hasStatement(s: IRI | Null, p: IRI | Null, o: Value | Null): Boolean =
			val result = getStatements(s, p, o)
			try result.hasNext finally result.close()

	describe("Reader-local subject properties"):
		it("parses a person with one read and sees updates on the next invocation"):
			val source = Source(IndexedSeq(
				RdfStatement(subject, vocab.hasFirstName, factory.createLiteral("Ada")),
				RdfStatement(subject, vocab.hasLastName, factory.createLiteral("Lovelace"))
			))
			val reader = new CpmetaReader:
				val metaVocab = vocab
			assert(reader.getPerson(subject)(using source).result.isDefined)
			assert(source.reads == 1)
			assert(source.closes == 1)
			source.statements = IndexedSeq.empty
			assert(reader.getPerson(subject)(using source).result.isEmpty)
			assert(source.reads == 2)
			assert(source.closes == 2)

		it("keeps absent properties local and rejects unrelated subject reads"):
			val source = Source(IndexedSeq.empty)
			val snapshot = SubjectStatements(subject)(using source)
			assert(!snapshot.hasStatement(subject, vocab.hasFirstName, null))
			assert(snapshot.getStatements(subject, null, null).isEmpty)
			assert(source.reads == 1)
			intercept[IllegalArgumentException]:
				snapshot.hasStatement(null, vocab.hasFirstName, null)
