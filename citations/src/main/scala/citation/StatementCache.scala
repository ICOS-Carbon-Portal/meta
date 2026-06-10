package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.event.LoggingAdapter
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Read-through cache of statements in a remote SPARQL repository.
 *
 * The metadata readers in meta access the triplestore through fine-grained
 * statement patterns (all statements of a subject, all subjects holding a given
 * property value). Against a remote SPARQL backend every such lookup is an HTTP
 * round-trip, which makes bulk citation materialization extremely slow. This
 * cache serves the same patterns from memory instead:
 *
 *   - per-subject entries hold the subject's complete statement set (quads, so
 *     that named-graph-scoped reads can be answered too),
 *   - per-object entries hold an object's complete incoming statement set,
 *   - per-(predicate, object) entries hold reverse-lookup results.
 *
 * Cache misses are answered with one targeted SPARQL query each, so correctness
 * never depends on prefetching. [[prefetch]] warms the cache for a batch of
 * subjects with a few VALUES-batched queries: the subjects' own statements,
 * their incoming statements (version/collection-membership lookups), and the
 * statements of resources they reference, a few hops out. Entries are LRU-bound,
 * so frequently shared resources (stations, specs, people, licences) stay cached
 * across batches while per-object satellites age out.
 *
 * Not thread-safe: meant to be used from the (sequential) materialization loop.
 */
class StatementCache(repo: Repository, log: LoggingAdapter):
	import StatementCache.*

	val factory: ValueFactory = repo.getValueFactory

	private val subjects = LruMap[IRI, IndexedSeq[Statement]](SubjectCacheSize)
	private val incoming = LruMap[Value, IndexedSeq[Statement]](IncomingCacheSize)
	private val reverse = LruMap[(IRI, Value), IndexedSeq[Statement]](ReverseCacheSize)
	private val interned = mutable.HashMap.empty[Value, Value]

	final class Snapshot(val queries: Long, val hits: Long, val misses: Long, val rows: Long):
		def minus(other: Snapshot) = Snapshot(
			queries - other.queries, hits - other.hits, misses - other.misses, rows - other.rows
		)
		override def toString = s"$queries queries ($rows rows), $hits cache hits, $misses misses"

	private var queryCount, hitCount, missCount, rowCount = 0L
	def stats: Snapshot = Snapshot(queryCount, hitCount, missCount, rowCount)

	/** All statements with the given subject, across all named graphs. */
	def subjectStatements(subj: IRI): IndexedSeq[Statement] =
		val cached = subjects.get(subj)
		if cached != null then
			hitCount += 1
			cached
		else
			missCount += 1
			fetchSubjects(IndexedSeq(subj))
			subjects.get(subj)

	/** All statements with the given object (optionally restricted to a predicate), across all named graphs. */
	def incomingStatements(pred: IRI | Null, obj: Value): IndexedSeq[Statement] =
		val allIncoming = incoming.get(obj)
		if allIncoming != null then
			hitCount += 1
			if pred == null then allIncoming
			else allIncoming.filter(_.getPredicate == pred)
		else if pred != null then
			val key = (pred, obj)
			val cached = reverse.get(key)
			if cached != null then
				hitCount += 1
				cached
			else
				missCount += 1
				val fetched = fetchReverse(pred, obj)
				reverse.put(key, fetched)
				// the readers nearly always follow up a reverse lookup by reading the
				// found subjects (e.g. memberships at a station), so bulk-fetch them now
				fetchSubjects(fetched.map(_.getSubject).collect{ case iri: IRI => iri }.filterNot(subjects.containsKey))
				fetched
		else
			missCount += 1
			val fetched = fetchIncoming(obj)
			incoming.put(obj, fetched)
			fetched

	/** Fallback for patterns the cache cannot serve (full scans); goes straight to the repository. */
	def uncachedStatements(
		subj: IRI | Null, pred: IRI | Null, obj: Value | Null, contexts: Seq[IRI]
	): CloseableIterator[Statement] =
		log.warning(s"Uncached statement pattern (subj $subj, pred $pred, obj $obj), querying the remote repository")
		queryCount += 1
		repo.access(conn => conn.getStatements(subj, pred, obj, false, contexts*))

	/**
	 * Warms the cache for a batch of subjects: bulk-fetches their statements and
	 * incoming statements, then iteratively the statements of (not yet cached)
	 * resources reachable from them, up to [[StatementCache.ExpansionDepth]] hops.
	 */
	def prefetch(batch: Seq[IRI]): Unit =
		val before = stats
		val startNanos = System.nanoTime()
		val incomingStmts = prefetchIncoming(batch.distinct.filterNot(incoming.containsKey))
		// subjects referring to the batch (deprecating versions, parent collections) get read too
		val referrers = incomingStmts.iterator.map(_.getSubject).collect{ case iri: IRI => iri }
		var toFetch: Seq[IRI] = (batch.iterator ++ referrers).distinct.filterNot(subjects.containsKey).toIndexedSeq
		var depth = 0
		while toFetch.nonEmpty && depth <= ExpansionDepth do
			val fetched = fetchSubjects(toFetch)
			depth += 1
			toFetch =
				if depth > ExpansionDepth then Nil
				else fetched.iterator
					.map(_.getObject)
					.collect{ case iri: IRI => iri }
					.filterNot(subjects.containsKey)
					.distinct
					.take(FrontierCap)
					.toIndexedSeq
		val delta = stats.minus(before)
		log.debug(
			f"Prefetched ${batch.size} subjects in ${(System.nanoTime() - startNanos) / 1e9}%.1f s: $delta"
		)

	/** Bulk-fetches and caches the complete statement sets of the given subjects (empty sets included). */
	private def fetchSubjects(subjs: Seq[IRI]): IndexedSeq[Statement] =
		val all = ArrayBuffer.empty[Statement]
		for chunk <- subjs.grouped(ValuesChunkSize) do
			val values = chunk.iterator.map(iri => s"<${iri.stringValue}>").mkString(" ")
			val query = s"SELECT ?s ?p ?o ?g WHERE { VALUES ?s { $values } GRAPH ?g { ?s ?p ?o } }"
			val bySubj = mutable.HashMap.empty[IRI, ArrayBuffer[Statement]]
			select(query): bs =>
				(bs.getValue("s"), bs.getValue("p"), bs.getValue("o"), bs.getValue("g")) match
					case (s: IRI, p: IRI, o: Value, g: Resource) =>
						val subj = intern(s)
						val st = factory.createStatement(subj, intern(p), internIfIri(o), intern(g))
						bySubj.getOrElseUpdate(subj, ArrayBuffer.empty) += st
					case _ => ()
			for subj <- chunk do
				val stmts = bySubj.get(subj).fold(NoStatements)(_.toIndexedSeq)
				subjects.put(intern(subj), stmts)
				all ++= stmts
		all.toIndexedSeq

	/** Bulk-fetches and caches the complete incoming statement sets of the given objects
	 *  (empty sets included); returns the fetched statements. */
	private def prefetchIncoming(objs: Seq[IRI]): IndexedSeq[Statement] =
		val all = ArrayBuffer.empty[Statement]
		for chunk <- objs.grouped(ValuesChunkSize) do
			val values = chunk.iterator.map(iri => s"<${iri.stringValue}>").mkString(" ")
			val query = s"SELECT ?s ?p ?o ?g WHERE { VALUES ?o { $values } GRAPH ?g { ?s ?p ?o } }"
			val byObj = mutable.HashMap.empty[Value, ArrayBuffer[Statement]]
			select(query): bs =>
				(bs.getValue("s"), bs.getValue("p"), bs.getValue("o"), bs.getValue("g")) match
					case (s: IRI, p: IRI, o: Value, g: Resource) =>
						val obj = internIfIri(o)
						byObj.getOrElseUpdate(obj, ArrayBuffer.empty) +=
							factory.createStatement(intern(s), intern(p), obj, intern(g))
					case _ => ()
			for obj <- chunk do
				val stmts = byObj.get(obj).fold(NoStatements)(_.toIndexedSeq)
				incoming.put(intern(obj), stmts)
				all ++= stmts
		all.toIndexedSeq

	private def fetchIncoming(obj: Value): IndexedSeq[Statement] =
		boundSelect("SELECT ?s ?p ?g WHERE { GRAPH ?g { ?s ?p ?theObj } }", "theObj" -> obj): bs =>
			(bs.getValue("s"), bs.getValue("p"), bs.getValue("g")) match
				case (s: IRI, p: IRI, g: Resource) =>
					Some(factory.createStatement(intern(s), intern(p), obj, intern(g)))
				case _ => None

	private def fetchReverse(pred: IRI, obj: Value): IndexedSeq[Statement] =
		boundSelect("SELECT ?s ?g WHERE { GRAPH ?g { ?s ?thePred ?theObj } }", "thePred" -> pred, "theObj" -> obj): bs =>
			(bs.getValue("s"), bs.getValue("g")) match
				case (s: IRI, g: Resource) =>
					Some(factory.createStatement(intern(s), pred, obj, intern(g)))
				case _ => None

	private def select(query: String)(handler: BindingSet => Unit): Unit =
		queryCount += 1
		repo.accessEagerly: conn =>
			val res = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
			try while res.hasNext() do
				rowCount += 1
				handler(res.next())
			finally res.close()

	private def boundSelect(
		query: String, bindings: (String, Value)*
	)(parser: BindingSet => Option[Statement]): IndexedSeq[Statement] =
		queryCount += 1
		repo.accessEagerly: conn =>
			val tq = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)
			for (name, value) <- bindings do tq.setBinding(name, value)
			val res = tq.evaluate()
			val buf = ArrayBuffer.empty[Statement]
			try while res.hasNext() do
				rowCount += 1
				buf ++= parser(res.next())
			finally res.close()
			buf.toIndexedSeq

	/** The remote tuple results allocate fresh Value instances per row; interning the
	 *  heavily repeated ones (IRIs of predicates, graphs, common resources) keeps the
	 *  cache's memory footprint down. */
	private def intern[V <: Value](v: V): V =
		if interned.size > InternCap then interned.clear()
		interned.getOrElseUpdate(v, v).asInstanceOf[V]

	private def internIfIri(v: Value): Value = v match
		case iri: IRI => intern(iri)
		case other => other

end StatementCache

object StatementCache:
	val ValuesChunkSize = 1000
	val ExpansionDepth = 3
	val FrontierCap = 20_000
	val SubjectCacheSize = 100_000
	val IncomingCacheSize = 20_000
	val ReverseCacheSize = 50_000
	val InternCap = 200_000

	private val NoStatements = IndexedSeq.empty[Statement]

	private final class LruMap[K, V](maxEntries: Int) extends java.util.LinkedHashMap[K, V](256, 0.75f, true):
		override def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean = size() > maxEntries

end StatementCache


/**
 * [[TriplestoreConnection]] view over a [[StatementCache]], used to run meta's
 * metadata readers against cached statements instead of the remote repository.
 * Named-graph scoping (`withContexts`, as applied by the RDF lenses) is honoured
 * by filtering the cached quads on their graph.
 */
class CachingConnection(
	cache: StatementCache,
	val primaryContext: IRI,
	val readContexts: Seq[IRI]
) extends TriplestoreConnection:

	def this(cache: StatementCache) = this(cache, null, Nil)

	override def factory: ValueFactory = cache.factory

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		if subject != null then
			wrap(cache.subjectStatements(subject).iterator.filter(matches(predicate, obj)))
		else if obj != null then
			wrap(cache.incomingStatements(predicate, obj).iterator.filter(matches(predicate, obj)))
		else
			cache.uncachedStatements(subject, predicate, obj, readContexts)

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		val iter = getStatements(subject, predicate, obj)
		try iter.hasNext finally iter.close()

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		CachingConnection(cache, primary, read)

	override def close(): Unit = ()

	private def matches(pred: IRI | Null, obj: Value | Null)(st: Statement): Boolean =
		(pred == null || st.getPredicate == pred) && (obj == null || st.getObject == obj)

	private def wrap(stmts: Iterator[Statement]): CloseableIterator[Statement] =
		val scoped =
			if readContexts.isEmpty then
				// no graph scoping: union over all graphs, deduplicated like a triple-level read
				stmts.distinctBy(st => (st.getSubject, st.getPredicate, st.getObject))
			else
				stmts.filter(st => readContexts.contains(st.getContext))
		CloseableIterator.Wrap(scoped, () => ())

end CachingConnection
