package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection

import scala.collection.mutable
import scala.util.Using

/**
 * A view of a triplestore connection that remembers what has been read through it, meant to be used
 * for the duration of one read-only operation, such as building a landing page.
 *
 * The readers look up the properties of a resource one by one, and often read the same resource many
 * times over (an organization, for example, is the submitter, the host and the responsible
 * organization at once). So, the first time a subject is looked at, all of its statements are read
 * at once, and the later lookups of its properties are answered from memory. Subjects with too many
 * statements to keep are not prefetched; lookups on them, as well as those without a subject, are
 * remembered as they are made.
 *
 * Views on other RDF graphs, made with `withContexts`, share the cache, keyed by their read contexts.
 */
private final class CachingConnection(
	inner: TriplestoreConnection, cache: CachingConnection.Cache
) extends TriplestoreConnection:
	import CachingConnection.*

	def this(inner: TriplestoreConnection) = this(inner, CachingConnection.Cache())

	override def primaryContext: IRI = inner.primaryContext
	override def readContexts: Seq[IRI] = inner.readContexts
	override def factory: ValueFactory = inner.factory

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		CachingConnection(inner.withContexts(primary, read), cache)

	override def close(): Unit = inner.close()

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		val statements = subjectStatements(subject) match
			case Some(all) => all.filter(matches(predicate, obj))
			case None => cache.lookups.getOrElseUpdate(
				(readContexts, subject, predicate, obj),
				Using.resource(inner.getStatements(subject, predicate, obj))(_.toIndexedSeq)
			)
		CloseableIterator.Wrap(statements.iterator, () => ())

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		subjectStatements(subject) match
			case Some(all) => all.exists(matches(predicate, obj))
			case None => inner.hasStatement(subject, predicate, obj)

	private def subjectStatements(subject: IRI | Null): Option[IndexedSeq[Statement]] =
		if subject == null then None
		else cache.subjects.getOrElseUpdate(
			(readContexts, subject),
			Using.resource(inner.getStatements(subject, null, null)): iter =>
				val statements = iter.take(MaxSubjectStatements + 1).toIndexedSeq
				Option.when(statements.length <= MaxSubjectStatements)(statements)
		)

end CachingConnection

private object CachingConnection:
	private val MaxSubjectStatements = 1000

	final class Cache:
		val subjects = mutable.HashMap.empty[(Seq[IRI], IRI), Option[IndexedSeq[Statement]]]
		val lookups = mutable.HashMap.empty[(Seq[IRI], IRI | Null, IRI | Null, Value | Null), IndexedSeq[Statement]]

	private def matches(predicate: IRI | Null, obj: Value | Null)(st: Statement): Boolean =
		(predicate == null || predicate == st.getPredicate) && (obj == null || obj == st.getObject)
