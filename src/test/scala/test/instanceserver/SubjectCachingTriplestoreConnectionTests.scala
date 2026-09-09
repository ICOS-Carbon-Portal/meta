package se.lu.nateko.cp.meta.test.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, SubjectCachingTriplestoreConnection, TriplestoreConnection}

class SubjectCachingTriplestoreConnectionTests extends AnyFunSpec:

	private val factory = SimpleValueFactory.getInstance()
	private val context1 = factory.createIRI("https://example.org/graph/1")
	private val context2 = factory.createIRI("https://example.org/graph/2")
	private val subject = factory.createIRI("https://example.org/subject")
	private val predicate1 = factory.createIRI("https://example.org/predicate/1")
	private val predicate2 = factory.createIRI("https://example.org/predicate/2")
	private val value1 = factory.createLiteral("value 1")
	private val value2 = factory.createLiteral("value 2")

	describe("SubjectCachingTriplestoreConnection"):

		it("uses one complete subject fetch for repeated property reads and existence checks"):
			val state = State(Map(
				context1 -> IndexedSeq(
					RdfStatement(subject, predicate1, value1),
					RdfStatement(subject, predicate2, value2)
				)
			))
			val cached = SubjectCachingTriplestoreConnection(CountingConnection(context1, Seq(context1), state))

			assert(cached.getStatements(subject, predicate1, null).map(_.obj).toIndexedSeq === IndexedSeq(value1))
			assert(cached.hasStatement(subject, predicate2, value2))
			assert(!cached.hasStatement(subject, predicate2, value1))
			assert(cached.getStatements(subject, null, null).size === 2)
			assert(state.getStatementsCalls === 1)
			assert(state.hasStatementCalls === 0)

		it("keeps cached subjects isolated by read contexts"):
			val state = State(Map(
				context1 -> IndexedSeq(RdfStatement(subject, predicate1, value1)),
				context2 -> IndexedSeq(RdfStatement(subject, predicate1, value2))
			))
			val cached = SubjectCachingTriplestoreConnection(CountingConnection(context1, Seq(context1), state))
			val graph1 = cached.withReadContexts(Seq(context1))
			val graph2 = cached.withReadContexts(Seq(context2))

			assert(graph1.getStatements(subject, predicate1, null).map(_.obj).toIndexedSeq === IndexedSeq(value1))
			assert(graph2.getStatements(subject, predicate1, null).map(_.obj).toIndexedSeq === IndexedSeq(value2))
			assert(graph1.hasStatement(subject, predicate1, value1))
			assert(state.getStatementsCalls === 2)

		it("delegates reverse lookups because they cannot be answered by a subject cache"):
			val state = State(Map(
				context1 -> IndexedSeq(RdfStatement(subject, predicate1, value1))
			))
			val cached = SubjectCachingTriplestoreConnection(CountingConnection(context1, Seq(context1), state))

			assert(cached.getStatements(null, predicate1, value1).size === 1)
			assert(cached.hasStatement(null, predicate1, value1))
			assert(state.getStatementsCalls === 1)
			assert(state.hasStatementCalls === 1)

		it("does not share entries between request-scoped wrappers"):
			val state = State(Map(
				context1 -> IndexedSeq(RdfStatement(subject, predicate1, value1))
			))
			val delegate = CountingConnection(context1, Seq(context1), state)

			SubjectCachingTriplestoreConnection(delegate).hasStatement(subject, predicate1, value1)
			SubjectCachingTriplestoreConnection(delegate).hasStatement(subject, predicate1, value1)

			assert(state.getStatementsCalls === 2)

	private final case class State(
		statementsByContext: Map[IRI, IndexedSeq[RdfStatement]],
		var getStatementsCalls: Int = 0,
		var hasStatementCalls: Int = 0
	)

	private final case class CountingConnection(
		primaryContext: IRI,
		readContexts: Seq[IRI],
		state: State
	) extends TriplestoreConnection with SparqlRunner:

		def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[RdfStatement] =
			state.getStatementsCalls += 1
			new CloseableIterator.Wrap(
				visibleStatements.iterator.filter: statement =>
					(subject == null || statement.subject == subject) &&
					(predicate == null || statement.predicate == predicate) &&
					(obj == null || statement.obj == obj),
				() => ()
			)

		def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
			state.hasStatementCalls += 1
			visibleStatements.exists: statement =>
				(subject == null || statement.subject == subject) &&
				(predicate == null || statement.predicate == predicate) &&
				(obj == null || statement.obj == obj)

		def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
			copy(primaryContext = primary, readContexts = read)

		def factory: ValueFactory = SubjectCachingTriplestoreConnectionTests.this.factory
		def close(): Unit = ()

		def evaluateGraphQuery(query: String): CloseableIterator[Statement] = CloseableIterator.empty
		def evaluateTupleQuery(query: String): CloseableIterator[BindingSet] = CloseableIterator.empty

		private def visibleStatements: IndexedSeq[RdfStatement] =
			if readContexts.isEmpty then state.statementsByContext.valuesIterator.flatten.toIndexedSeq
			else readContexts.iterator.flatMap(state.statementsByContext.getOrElse(_, IndexedSeq.empty)).toIndexedSeq
