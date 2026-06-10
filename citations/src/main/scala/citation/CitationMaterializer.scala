package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
 * Writes the three "magic" virtual triples (hasBiblioInfo, hasCitationString,
 * dcterms:license) as real triples into a dedicated derived named graph.
 *
 * Replaces the on-the-fly enrichment that used to live in EnrichingSail /
 * StatementsEnricher: with a remote SPARQL backend we cannot inject values at
 * query-evaluation time, so we materialize them ahead of time instead.
 *
 * `materializeAll` is incremental: it leaves the derived graph in place and only
 * materializes citations for citable subjects that do not already have any triple
 * in the derived graph. This avoids re-fetching from DataCite on every run.
 *
 * The main pass is a pipeline over batches of subjects: while one batch's
 * citations are being computed (in parallel slices, against a [[StatementCache]]
 * that answers the readers' statement patterns from memory), the next batches
 * are already being prefetched from the triplestore and the previous batch's
 * triples are being written, so SPARQL reads, computation and writes overlap.
 *
 * Subjects whose citation needs DataCite data that is not cached yet are not
 * materialized in the main pass: they are pushed to a [[DataCiteQueue]] (which
 * triggers the lookups immediately) and materialized, in push order, as their
 * DataCite lookups complete, concurrently with the main pass. A run finishes
 * when both the main pass and the queue are done.
 */
class CitationMaterializer(
	repo: Repository,
	citer: CitationProvider,
	derivedGraph: URI
)(using system: ActorSystem):
	import CitationMaterializer.*

	private val log = Logging.getLogger(system, this)
	private given factory: ValueFactory = repo.getValueFactory
	private given Materializer = Materializer.matFromSystem(using system)
	private val graphIri: IRI = factory.createIRI(derivedGraph.toString)
	private val blockingEc: ExecutionContext = system.dispatchers.lookup(BlockingDispatcher)
	import citer.metaVocab

	def materializeAll()(using ExecutionContext): Future[Int] =
		val startNanos = System.nanoTime()
		val cache = new StatementCache(repo, log)
		val cacheConn = new CachingConnection(cache)
		val dcQueue = new DataCiteQueue(citer.doiCiter, subjs => writeDataCiteBatch(subjs, cache, cacheConn))

		log.info(s"Citation materialization started (graph $derivedGraph)")
		log.info("Listing citable subjects...")
		val listStartNanos = System.nanoTime()
		val allSubjectsFut = Future(listCitableSubjects())(using blockingEc)
		val materializedFut = Future(listMaterializedSubjects())(using blockingEc)

		val mainPass =
			for
				allSubjects <- allSubjectsFut
				alreadyMaterialized <- materializedFut
				subjects = allSubjects.filterNot(alreadyMaterialized.contains)
				_ = log.info(
					f"Found ${allSubjects.size} citable subjects in ${(System.nanoTime() - listStartNanos) / 1e9}%.1f s " +
					f"(${alreadyMaterialized.size} already materialized, ${subjects.size} to materialize), materializing citations..."
				)
				written <- materializeInBatches(subjects, cache, cacheConn, dcQueue)
			yield written

		mainPass.transformWith:
			case scala.util.Failure(err) =>
				// always shut the queue down, so no stream outlives the run
				dcQueue.drain().transform(_ => scala.util.Failure(err))
			case Success(mainWritten) =>
				if dcQueue.pending > 0 then log.info(
					s"Main pass done, wrote $mainWritten triples; waiting for the DataCite queue (${dcQueue.pending} subjects pending)"
				)
				dcQueue.drain().map: queueWritten =>
					val written = mainWritten + queueWritten
					val skipped =
						if dcQueue.failed + dcQueue.dropped == 0 then ""
						else s" (${dcQueue.failed} subjects skipped on DataCite failures, ${dcQueue.dropped} dropped from the queue)"
					log.info(
						f"Citation materialization finished, wrote $written triples " +
						f"($queueWritten via the DataCite queue)$skipped in ${(System.nanoTime() - startNanos) / 1e9}%.0f s"
					)
					written

	private def listCitableSubjects(): IndexedSeq[IRI] =
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  ?s a ?t .
			|  FILTER(?t IN (<${metaVocab.dataObjectClass}>, <${metaVocab.docObjectClass}>, <${metaVocab.collectionClass}>))
			|}""".stripMargin
		querySubjects(q).toIndexedSeq

	/** Subjects that already have at least one triple in the derived citations graph. */
	private def listMaterializedSubjects(): Set[IRI] =
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  GRAPH <$derivedGraph> { ?s ?p ?o }
			|}""".stripMargin
		querySubjects(q).toSet

	private def querySubjects(query: String): collection.Seq[IRI] =
		val conn = repo.getConnection()
		try
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
			val buf = ArrayBuffer.empty[IRI]
			try
				while result.hasNext() do
					result.next().getValue("s") match
						case iri: IRI => buf += iri
						case _ => ()
			finally result.close()
			buf
		finally conn.close()

	/** Per-batch context flowing through the pipeline stages. */
	private case class Batch(
		chunk: Seq[(IRI, Int)],
		idx: Int,
		statsBefore: StatementCache.Snapshot,
		prefetchMs: Long = 0,
		computeMs: Long = 0,
		deferred: Int = 0,
		triples: IndexedSeq[Statement] = IndexedSeq.empty
	)

	/**
	 * The main pass as a pipeline: prefetch (up to [[CitationMaterializer.PrefetchAhead]]
	 * batches in advance) -> compute (one batch at a time, internally parallelized into
	 * [[CitationMaterializer.ComputeParallelism]] slices) -> write (overlapping with the
	 * computation of subsequent batches).
	 */
	private def materializeInBatches(
		subjects: IndexedSeq[IRI], cache: StatementCache, cacheConn: CachingConnection, dcQueue: DataCiteQueue
	)(using ExecutionContext): Future[Int] =
		val total = subjects.size
		val progress = new Progress(total, batchCount = (total + WriteBatchSize - 1) / WriteBatchSize)

		Source
			.fromIterator(() => subjects.zipWithIndex.grouped(WriteBatchSize).zipWithIndex)
			.mapAsync(PrefetchAhead): (chunk, batchIdx) =>
				Future {
					val t0 = System.nanoTime()
					val statsBefore = cache.stats
					cache.prefetch(chunk.map(_._1))
					Batch(chunk, batchIdx, statsBefore, prefetchMs = (System.nanoTime() - t0) / 1000000)
				}(using blockingEc)
			.mapAsync(1)(batch => computeBatch(batch, total, cacheConn, dcQueue))
			.mapAsync(WriteAhead): batch =>
				Future {
					val t0 = System.nanoTime()
					repo.transact: conn =>
						for t <- batch.triples do conn.add(t, graphIri)
					.get
					progress.logBatch(batch, writeMs = (System.nanoTime() - t0) / 1000000, sparql = cache.stats.minus(batch.statsBefore))
					batch.triples.size
				}(using blockingEc)
			.runWith(Sink.fold(0)(_ + _))

	/** Computes a batch's citation triples in parallel slices; deferred (DataCite-pending)
	 *  subjects are pushed to the queue in subject order after the slices join. */
	private def computeBatch(
		batch: Batch, total: Int, cacheConn: CachingConnection, dcQueue: DataCiteQueue
	)(using ExecutionContext): Future[Batch] =
		val t0 = System.nanoTime()
		val sliceSize = math.max(1, (batch.chunk.size + ComputeParallelism - 1) / ComputeParallelism)
		val slices = batch.chunk.grouped(sliceSize).toIndexedSeq

		Future
			.traverse(slices): slice =>
				Future {
					val triples = ArrayBuffer.empty[Statement]
					val deferred = ArrayBuffer.empty[(IRI, Doi, Int)]
					for (subj, idx) <- slice do
						subjectDoi(subj, cacheConn) match
							case Some(doi) if !dataCiteReady(doi) =>
								deferred += ((subj, doi, idx))
								log.debug(s"[${idx + 1}/$total] Deferred $subj to the DataCite queue (DOI $doi)")
							case _ =>
								triples ++= triplesFor(subj, s"${idx + 1}/$total", cacheConn)
					(triples, deferred)
				}(using blockingEc)
			.map: results =>
				for
					(_, deferred) <- results
					(subj, doi, _) <- deferred.sortBy(_._3)
				do dcQueue.push(subj, doi)
				batch.copy(
					computeMs = (System.nanoTime() - t0) / 1000000,
					deferred = results.map(_._2.size).sum,
					triples = results.flatMap(_._1).toIndexedSeq
				)

	/** Materializes a batch of subjects coming back from the DataCite queue; runs
	 *  concurrently with the main pass. */
	private def writeDataCiteBatch(subjs: Seq[IRI], cache: StatementCache, cacheConn: CachingConnection): Int =
		cache.prefetch(subjs)
		val triples = subjs.flatMap(subj => triplesFor(subj, "DataCite queue", cacheConn)).toIndexedSeq
		repo.transact: conn =>
			for t <- triples do conn.add(t, graphIri)
		.get
		log.info(s"DataCite queue: materialized ${subjs.size} subjects (${triples.size} triples)")
		triples.size

	/** The first valid DOI of the subject, if any. */
	private def subjectDoi(subj: IRI, conn: CachingConnection): Option[Doi] =
		StatementSource.getStringValues(subj, metaVocab.hasDoi)(using conn)
			.flatMap(Doi.parse(_).toOption)
			.headOption

	/** Whether all DataCite lookups needed to cite the given DOI have already succeeded.
	 *  Triggers (throttled, asynchronous) lookups of whatever is missing as a side
	 *  effect, so a deferred subject's DataCite data is being fetched while it waits
	 *  in the queue. */
	private def dataCiteReady(doi: Doi): Boolean =
		import citer.doiCiter
		val citationsReady = DataCiteQueue.NeededStyles
			.map(style => doiCiter.getCitationEager(doi, style))
			.forall:
				case Some(Success(_)) => true
				case _ => false
		val doiMetaReady = doiCiter.getDoiEager(doi) match
			case Some(Success(_)) => true
			case _ => false
		citationsReady && doiMetaReady

	private def triplesFor(subj: IRI, tag: String, conn: CachingConnection): IndexedSeq[Statement] =
		val startNanos = System.nanoTime()
		log.debug(s"[$tag] Materializing citation for $subj ...")
		val out = ArrayBuffer.empty[Statement]

		val refs: Option[References] = citer.getReferences(subj, conn)
		val hasBiblio = biblioLiteral(refs).map: lit =>
			out += factory.createStatement(subj, metaVocab.hasBiblioInfo, lit)
		.isDefined

		val citationOpt = refs.fold(citer.getCitation(subj, conn))(_.citationString)
		citationOpt.foreach: cit =>
			out += factory.createStatement(subj, metaVocab.hasCitationString, factory.createStringLiteral(cit))

		val licenceOpt = refs.fold(citer.getLicence(subj, conn))(_.licence)
		licenceOpt.foreach: lic =>
			out += factory.createStatement(subj, metaVocab.dcterms.license, lic.url.toRdf)

		val durMs = (System.nanoTime() - startNanos) / 1000000
		if out.isEmpty then
			log.info(s"[$tag] No citation triples produced for $subj (${durMs} ms)")
		else
			log.info(
				s"[$tag] Materialized ${out.size} triples for $subj in ${durMs} ms " +
				s"[biblio=$hasBiblio, citation=${citationOpt.isDefined}, licence=${licenceOpt.isDefined}]"
			)
		out.toIndexedSeq

	private def biblioLiteral(refs: Option[References]): Option[Value] =
		import spray.json.*
		import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
		refs.map(js => factory.createStringLiteral(js.toJson.compactPrint))

	/** Accumulates and logs main-pass progress; thread-safe (write stages may overlap). */
	private class Progress(total: Int, batchCount: Int):
		private val startNanos = System.nanoTime()
		private var processed, written, deferred = 0

		def logBatch(batch: Batch, writeMs: Long, sparql: StatementCache.Snapshot): Unit = synchronized:
			processed += batch.chunk.size
			written += batch.triples.size
			deferred += batch.deferred
			val elapsedS = (System.nanoTime() - startNanos) / 1e9
			val rate = if elapsedS > 0 then processed / elapsedS else 0.0
			val etaS = if rate > 0 then (total - processed) / rate else 0.0
			log.info(
				f"Citation materialization progress: batch ${batch.idx + 1}/$batchCount " +
				f"(prefetch ${batch.prefetchMs} ms, compute ${batch.computeMs} ms, write ${writeMs} ms), " +
				f"$processed/$total subjects processed (${100.0 * processed / total}%.1f%%), " +
				f"$written triples written so far, $deferred deferred to the DataCite queue, " +
				f"${elapsedS}%.0f s elapsed, ${rate}%.1f subj/s, ETA ${etaS}%.0f s; sparql: $sparql"
			)

end CitationMaterializer

object CitationMaterializer:
	val WriteBatchSize = 1000

	/** How many batches may be prefetching ahead of the one being computed. Keep small:
	 *  each in-flight batch occupies StatementCache LRU space; raising this without
	 *  raising the cache caps invites thrashing. */
	val PrefetchAhead = 2

	/** How many batch writes may be in flight while later batches compute. */
	val WriteAhead = 2

	/** Parallel slices per batch during citation computation. */
	val ComputeParallelism = math.min(8, math.max(2, Runtime.getRuntime.availableProcessors()))

	/** All blocking work (SPARQL queries, writes, computation with occasional cache-miss
	 *  fetches) runs here rather than on the default dispatcher. The StatementCache's
	 *  chunk queries have their own separate pool, so awaiting them from here cannot
	 *  self-starve. */
	val BlockingDispatcher = "akka.actor.default-blocking-io-dispatcher"

end CitationMaterializer
