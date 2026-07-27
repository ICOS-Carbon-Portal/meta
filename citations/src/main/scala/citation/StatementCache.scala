package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.event.LoggingAdapter
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.{Executors, ThreadFactory}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

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
 * subjects with a few VALUES-batched queries (issued concurrently, up to
 * [[StatementCache.MaxConcurrentChunkQueries]] at a time): the subjects' own
 * statements, their incoming statements (version/collection-membership lookups),
 * and the statements of resources they reference, a few hops out. Entries are
 * LRU-bound, so frequently shared resources (stations, specs, people, licences)
 * stay cached across batches while per-object satellites age out.
 *
 * Thread-safe and built for concurrent use (parallel compute slices, pipelined
 * prefetching, the DataCite queue worker): the internal lock guards only the
 * cache maps and is never held across remote fetches. Concurrent misses on the
 * same key may duplicate a fetch; the results are identical, so this is only a
 * small waste, not a correctness issue.
 */
class StatementCache(repo: Repository, log: LoggingAdapter) {
	import StatementCache.*

	val factory: ValueFactory = repo.getValueFactory

	/** Guards the three LRU maps below; never held across remote fetches. */
	private val lock = new Object
	private val subjects = LruMap[IRI, IndexedSeq[Statement]](SubjectCacheSize)
	private val incoming = LruMap[Value, IndexedSeq[Statement]](IncomingCacheSize)
	private val reverse = LruMap[(IRI, Value), IndexedSeq[Statement]](ReverseCacheSize)

	private val interned = TrieMap.empty[Value, Value]
	private val internedCount = AtomicInteger(0)

	private val queryCount, hitCount, missCount, rowCount = AtomicLong(0)
	def stats: Snapshot = Snapshot(queryCount.get, hitCount.get, missCount.get, rowCount.get)

	/** All statements with the given subject, across all named graphs. */
	def subjectStatements(subj: IRI): IndexedSeq[Statement] = {
		val cached = lock.synchronized(subjects.get(subj))
		if cached != null then {
			hitCount.incrementAndGet()
			cached
		}
		else {
			missCount.incrementAndGet()
			// single-subject fetch: all returned statements belong to subj
			fetchSubjects(IndexedSeq(subj))
		}
	}

	/** All statements with the given object (optionally restricted to a predicate), across all named graphs. */
	def incomingStatements(pred: IRI | Null, obj: Value): IndexedSeq[Statement] = {
		val allIncoming = lock.synchronized(incoming.get(obj))
		if allIncoming != null then {
			hitCount.incrementAndGet()
			if pred == null then allIncoming
			else allIncoming.filter(_.getPredicate == pred)
		}
		else if pred != null then {
			val key = (pred, obj)
			val cached = lock.synchronized(reverse.get(key))
			if cached != null then {
				hitCount.incrementAndGet()
				cached
			}
			else {
				missCount.incrementAndGet()
				val fetched = fetchReverse(pred, obj)
				lock.synchronized(reverse.put(key, fetched))
				// the readers nearly always follow up a reverse lookup by reading the
				// found subjects (e.g. memberships at a station), so bulk-fetch them now
				fetchSubjects(fetched.map(_.getSubject).collect{ case iri: IRI => iri }.filterNot(isCachedSubject))
				fetched
			}
		}
		else {
			missCount.incrementAndGet()
			val fetched = fetchIncoming(obj)
			lock.synchronized(incoming.put(obj, fetched))
			fetched
		}
	}

	/** Fallback for patterns the cache cannot serve (full scans); goes straight to the repository. */
	def uncachedStatements(
		subj: IRI | Null, pred: IRI | Null, obj: Value | Null, contexts: Seq[IRI]
	): CloseableIterator[Statement] = {
		log.warning(s"Uncached statement pattern (subj $subj, pred $pred, obj $obj), querying the remote repository")
		queryCount.incrementAndGet()
		repo.access(conn => conn.getStatements(subj, pred, obj, false, contexts*))
	}

	/**
	 * Warms the cache for a batch of subjects: bulk-fetches their statements and
	 * incoming statements, then iteratively the statements of (not yet cached)
	 * resources reachable from them, up to [[StatementCache.ExpansionDepth]] hops.
	 * Blocking; safe to run concurrently with reads and with other prefetches.
	 */
	def prefetch(batch: Seq[IRI]): Unit = {
		val before = stats
		val startNanos = System.nanoTime()
		val incomingStmts = prefetchIncoming(
			batch.distinct.filterNot(o => lock.synchronized(incoming.containsKey(o)))
		)
		// subjects referring to the batch (deprecating versions, parent collections) get read too
		val referrers = incomingStmts.iterator.map(_.getSubject).collect{ case iri: IRI => iri }
		var toFetch: Seq[IRI] = (batch.iterator ++ referrers).distinct.filterNot(isCachedSubject).toIndexedSeq
		var depth = 0
		while toFetch.nonEmpty && depth <= ExpansionDepth do {
			val fetched = fetchSubjects(toFetch)
			depth += 1
			toFetch =
				if depth > ExpansionDepth then Nil
				else fetched.iterator
					.map(_.getObject)
					.collect{ case iri: IRI => iri }
					.filterNot(isCachedSubject)
					.distinct
					.take(FrontierCap)
					.toIndexedSeq
		}
		val delta = stats.minus(before)
		log.debug(
			f"Prefetched ${batch.size} subjects in ${(System.nanoTime() - startNanos) / 1e9}%.1f s: $delta"
		)
	}

	private def isCachedSubject(subj: IRI): Boolean = lock.synchronized(subjects.containsKey(subj))

	/** Bulk-fetches and caches the complete statement sets of the given subjects
	 *  (empty sets included); returns the fetched statements. */
	private def fetchSubjects(subjs: Seq[IRI]): IndexedSeq[Statement] =
		runChunked(subjs) { chunk =>
			val values = chunk.iterator.map(iri => s"<${iri.stringValue}>").mkString(" ")
			val query = s"SELECT ?s ?p ?o ?g WHERE { VALUES ?s { $values } GRAPH ?g { ?s ?p ?o } }"
			val bySubj = mutable.HashMap.empty[IRI, ArrayBuffer[Statement]]
			select(query) { bs =>
				(bs.getValue("s"), bs.getValue("p"), bs.getValue("o"), bs.getValue("g")) match {
					case (s: IRI, p: IRI, o: Value, g: Resource) =>
						val subj = intern(s)
						val st = factory.createStatement(subj, intern(p), internIfIri(o), intern(g))
						bySubj.getOrElseUpdate(subj, ArrayBuffer.empty) += st
					case _ => ()
				}
			}
			val entries = chunk.map { subj =>
				intern(subj) -> bySubj.get(subj).fold(NoStatements)(_.toIndexedSeq)
			}
			lock.synchronized {
				for ((subj, stmts) <- entries) subjects.put(subj, stmts)
			}
			entries.iterator.flatMap(_._2).toIndexedSeq
		}

	/** Bulk-fetches and caches the complete incoming statement sets of the given objects
	 *  (empty sets included); returns the fetched statements. */
	private def prefetchIncoming(objs: Seq[IRI]): IndexedSeq[Statement] =
		runChunked(objs) { chunk =>
			val values = chunk.iterator.map(iri => s"<${iri.stringValue}>").mkString(" ")
			val query = s"SELECT ?s ?p ?o ?g WHERE { VALUES ?o { $values } GRAPH ?g { ?s ?p ?o } }"
			val byObj = mutable.HashMap.empty[Value, ArrayBuffer[Statement]]
			select(query) { bs =>
				(bs.getValue("s"), bs.getValue("p"), bs.getValue("o"), bs.getValue("g")) match {
					case (s: IRI, p: IRI, o: Value, g: Resource) =>
						val obj = internIfIri(o)
						byObj.getOrElseUpdate(obj, ArrayBuffer.empty) +=
							factory.createStatement(intern(s), intern(p), obj, intern(g))
					case _ => ()
				}
			}
			val entries = chunk.map { obj =>
				intern(obj) -> byObj.get(obj).fold(NoStatements)(_.toIndexedSeq)
			}
			lock.synchronized {
				for ((obj, stmts) <- entries) incoming.put(obj, stmts)
			}
			entries.iterator.flatMap(_._2).toIndexedSeq
		}

	/** Runs the chunk queries of a bulk fetch, concurrently when there are several chunks. */
	private def runChunked(items: Seq[IRI])(fetchChunk: Seq[IRI] => IndexedSeq[Statement]): IndexedSeq[Statement] = {
		val chunks = items.grouped(ValuesChunkSize).toIndexedSeq
		chunks match {
			case IndexedSeq() => IndexedSeq.empty
			case IndexedSeq(single) => fetchChunk(single)
			case _ =>
				given ExecutionContext = chunkQueryEc
				val fut = Future.traverse(chunks)(chunk => Future(fetchChunk(chunk)))
				Await.result(fut, ChunkQueryTimeout).flatten
		}
	}

	private def fetchIncoming(obj: Value): IndexedSeq[Statement] =
		boundSelect("SELECT ?s ?p ?g WHERE { GRAPH ?g { ?s ?p ?theObj } }", "theObj" -> obj) { bs =>
			(bs.getValue("s"), bs.getValue("p"), bs.getValue("g")) match {
				case (s: IRI, p: IRI, g: Resource) =>
					Some(factory.createStatement(intern(s), intern(p), obj, intern(g)))
				case _ => None
			}
		}

	private def fetchReverse(pred: IRI, obj: Value): IndexedSeq[Statement] =
		boundSelect("SELECT ?s ?g WHERE { GRAPH ?g { ?s ?thePred ?theObj } }", "thePred" -> pred, "theObj" -> obj) { bs =>
			(bs.getValue("s"), bs.getValue("g")) match {
				case (s: IRI, g: Resource) =>
					Some(factory.createStatement(intern(s), pred, obj, intern(g)))
				case _ => None
			}
		}

	private def select(query: String)(handler: BindingSet => Unit): Unit = {
		queryCount.incrementAndGet()
		repo.accessEagerly { conn =>
			val res = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
			try while res.hasNext() do {
				rowCount.incrementAndGet()
				handler(res.next())
			}
			finally res.close()
		}
	}

	private def boundSelect(
		query: String, bindings: (String, Value)*
	)(parser: BindingSet => Option[Statement]): IndexedSeq[Statement] = {
		queryCount.incrementAndGet()
		repo.accessEagerly { conn =>
			val tq = conn.prepareTupleQuery(QueryLanguage.SPARQL, query)
			for ((name, value) <- bindings) tq.setBinding(name, value)
			val res = tq.evaluate()
			val buf = ArrayBuffer.empty[Statement]
			try while res.hasNext() do {
				rowCount.incrementAndGet()
				buf ++= parser(res.next())
			}
			finally res.close()
			buf.toIndexedSeq
		}
	}

	/** The remote tuple results allocate fresh Value instances per row; interning the
	 *  heavily repeated ones (IRIs of predicates, graphs, common resources) keeps the
	 *  cache's memory footprint down. */
	private def intern[V <: Value](v: V): V =
		interned.putIfAbsent(v, v) match {
			case Some(existing) => existing.asInstanceOf[V]
			case None =>
				if internedCount.incrementAndGet() > InternCap then {
					interned.clear()
					internedCount.set(0)
				}
				v
		}

	private def internIfIri(v: Value): Value = v match {
		case iri: IRI => intern(iri)
		case other => other
	}

} // end StatementCache

object StatementCache {
	val ValuesChunkSize = 1000
	val ExpansionDepth = 3
	val FrontierCap = 20_000
	val SubjectCacheSize = 200_000
	val IncomingCacheSize = 20_000
	val ReverseCacheSize = 50_000
	val InternCap = 200_000

	val MaxConcurrentChunkQueries = 6
	val ChunkQueryTimeout = 10.minutes

	final case class Snapshot(queries: Long, hits: Long, misses: Long, rows: Long) {
		def minus(other: Snapshot) = Snapshot(
			queries - other.queries, hits - other.hits, misses - other.misses, rows - other.rows
		)
		override def toString = s"$queries queries ($rows rows), $hits cache hits, $misses misses"
	}

	private val NoStatements = IndexedSeq.empty[Statement]

	/** Dedicated bounded pool for the bulk chunk queries: keeps the global SPARQL query
	 *  concurrency capped and independent of the dispatchers the callers run on (so a
	 *  caller awaiting its chunks can never starve the chunks themselves). */
	private lazy val chunkQueryEc: ExecutionContext = {
		val threadCount = AtomicInteger(0)
		val tf: ThreadFactory = runnable => {
			val t = new Thread(runnable, s"citation-sparql-chunk-${threadCount.incrementAndGet()}")
			t.setDaemon(true)
			t
		}
		ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(MaxConcurrentChunkQueries, tf))
	}

	private final class LruMap[K, V](maxEntries: Int) extends java.util.LinkedHashMap[K, V](256, 0.75f, true) {
		override def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean = size() > maxEntries
	}

} // end StatementCache


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
) extends TriplestoreConnection {

	def this(cache: StatementCache) = this(cache, null, Nil)

	override def factory: ValueFactory = cache.factory

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		if subject != null then
			wrap(cache.subjectStatements(subject).iterator.filter(matches(predicate, obj)))
		else if obj != null then
			wrap(cache.incomingStatements(predicate, obj).iterator.filter(matches(predicate, obj)))
		else
			cache.uncachedStatements(subject, predicate, obj, readContexts)

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean = {
		val iter = getStatements(subject, predicate, obj)
		try iter.hasNext finally iter.close()
	}

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		CachingConnection(cache, primary, read)

	override def close(): Unit = ()

	private def matches(pred: IRI | Null, obj: Value | Null)(st: Statement): Boolean =
		(pred == null || st.getPredicate == pred) && (obj == null || st.getObject == obj)

	private def wrap(stmts: Iterator[Statement]): CloseableIterator[Statement] = {
		val scoped =
			if readContexts.isEmpty then
				// no graph scoping: union over all graphs, deduplicated like a triple-level read
				stmts.distinctBy(st => (st.getSubject, st.getPredicate, st.getObject))
			else
				stmts.filter(st => readContexts.contains(st.getContext))
		CloseableIterator.Wrap(scoped, () => ())
	}

} // end CachingConnection
