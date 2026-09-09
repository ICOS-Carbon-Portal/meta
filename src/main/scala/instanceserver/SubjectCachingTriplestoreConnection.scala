package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.BindingSet
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}

import scala.collection.mutable

/**
 * Request-scoped read-through cache for outgoing statements.
 *
 * Property-oriented metadata readers usually ask for several predicates of the same subject.
 * Against a remote SPARQLRepository every such lookup is a separate HTTP request. This wrapper
 * fetches all statements for a subject on the first lookup and serves subsequent lookups locally.
 * Cache entries include the active read contexts so graph lenses remain isolated.
 *
 * Queries without a known subject are delegated because a subject cache cannot answer them
 * completely.
 */
final class SubjectCachingTriplestoreConnection private (
	delegate: TriplestoreConnection,
	sparqlRunner: SparqlRunner,
	cache: mutable.Map[SubjectCachingTriplestoreConnection.CacheKey, IndexedSeq[RdfStatement]]
) extends TriplestoreConnection with SparqlRunner:

	import SubjectCachingTriplestoreConnection.CacheKey

	override def getStatements(
		subject: IRI | Null,
		predicate: IRI | Null,
		obj: Value | Null
	): CloseableIterator[RdfStatement] =
		if subject == null then delegate.getStatements(null, predicate, obj)
		else
			val matching = statementsFor(subject).iterator.filter: statement =>
				(predicate == null || statement.predicate == predicate) &&
				(obj == null || statement.obj == obj)
			new CloseableIterator.Wrap(matching, () => ())

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		if subject == null then delegate.hasStatement(null, predicate, obj)
		else statementsFor(subject).exists: statement =>
			(predicate == null || statement.predicate == predicate) &&
			(obj == null || statement.obj == obj)

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		new SubjectCachingTriplestoreConnection(delegate.withContexts(primary, read), sparqlRunner, cache)

	override def primaryContext: IRI = delegate.primaryContext
	override def readContexts: Seq[IRI] = delegate.readContexts
	override def factory: ValueFactory = delegate.factory
	override def close(): Unit = delegate.close()

	override def evaluateGraphQuery(query: String): CloseableIterator[Statement] =
		sparqlRunner.evaluateGraphQuery(query)

	override def evaluateTupleQuery(query: String): CloseableIterator[BindingSet] =
		sparqlRunner.evaluateTupleQuery(query)

	private def statementsFor(subject: IRI): IndexedSeq[RdfStatement] = cache.synchronized:
		cache.getOrElseUpdate(
			CacheKey(readContexts.toIndexedSeq, subject),
			delegate.getStatements(subject, null, null).toIndexedSeq
		)

end SubjectCachingTriplestoreConnection

object SubjectCachingTriplestoreConnection:
	private final case class CacheKey(readContexts: IndexedSeq[IRI], subject: IRI)

	def apply(delegate: TriplestoreConnection & SparqlRunner): TriplestoreConnection & SparqlRunner =
		new SubjectCachingTriplestoreConnection(delegate, delegate, mutable.HashMap.empty)
