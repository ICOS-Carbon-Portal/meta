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
import se.lu.nateko.cp.meta.core.data.{Licence, References}
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Writes the three "magic" virtual triples (hasBiblioInfo, hasCitationString,
 * dcterms:license) as real triples into a dedicated derived named graph.
 *
 * Replaces the on-the-fly enrichment that used to live in EnrichingSail /
 * StatementsEnricher: with a remote SPARQL backend we cannot inject values at
 * query-evaluation time, so we materialize them ahead of time instead.
 *
 * The default `materializeAll` run is incremental. Explicit reconciliation modes
 * can also recompute existing subjects, remove stale subjects, and refresh cached
 * DataCite fields.
 *
 * The main pass is a pipeline over batches of subjects: while one batch's
 * citations are being computed (in parallel slices, against a [[StatementCache]]
 * that answers the readers' statement patterns from memory), the next batches
 * are already being prefetched from the triplestore and the previous batch's
 * triples are being written, so SPARQL reads, computation and writes overlap.
 *
 * DOI subjects have their triplestore-dependent references prepared once in the
 * main pass, then are completed concurrently by [[DataCiteQueue]]. A run finishes
 * when both the main pass and the queue are done.
 */
class CitationMaterializer(
	repo: Repository,
	citer: CitationProvider,
	derivedGraph: URI
)(using system: ActorSystem) {
	import CitationMaterializer.*

	private val log = Logging.getLogger(system, this)
	private given factory: ValueFactory = repo.getValueFactory
	private given Materializer = Materializer.matFromSystem(using system)
	private val graphIri: IRI = factory.createIRI(derivedGraph.toString)
	private val blockingEc: ExecutionContext = system.dispatchers.lookup(BlockingDispatcher)
	import citer.metaVocab
	private val reconciler = CitationReconciler(
		repo, graphIri, metaVocab.hasBiblioInfo, metaVocab.hasCitationString, WriteBatchSize
	)

	def materializeAll()(using ExecutionContext): Future[Int] =
		materializeAll(ReconcileMode.MissingOnly)

	def materializeAll(mode: ReconcileMode)(using ExecutionContext): Future[Int] = {
		val startNanos = System.nanoTime()
		val cache = new StatementCache(repo, log)
		val cacheConn = new CachingConnection(cache)
		val dcQueue = new DataCiteQueue(citer.doiCiter, writeDataCiteBatch)
		val refreshedDois = ConcurrentHashMap.newKeySet[Doi]()

		log.info(s"Citation materialization started in $mode mode (graph $derivedGraph)")
		log.info("Listing citable subjects...")
		val listStartNanos = System.nanoTime()
		val allSubjectsFut = Future(listCitableSubjects())(using blockingEc)
		val materializedFut = Future(listMaterializedSubjects())(using blockingEc)
		val derivedSubjectsFut =
			if (mode.removesStale) Future(listDerivedSubjects())(using blockingEc)
			else Future.successful(Set.empty[IRI])

		val mainPass =
			for {
				allSubjects <- allSubjectsFut
				alreadyMaterialized <- materializedFut
				derivedSubjects <- derivedSubjectsFut
				subjects =
					if (mode.reconcilesExisting) allSubjects
					else allSubjects.filterNot(alreadyMaterialized.contains)
				stale = if (mode.removesStale) derivedSubjects -- allSubjects.toSet else Set.empty[IRI]
				_ = log.info(
					f"Found ${allSubjects.size} citable subjects in ${(System.nanoTime() - listStartNanos) / 1e9}%.1f s " +
					f"(${alreadyMaterialized.size} already materialized, ${subjects.size} to reconcile, ${stale.size} stale), materializing citations..."
				)
				written <- materializeInBatches(subjects, cache, cacheConn, dcQueue, mode, refreshedDois)
			}
			yield written -> stale

		mainPass.transformWith {
			case scala.util.Failure(err) =>
				// always shut the queue down, so no stream outlives the run
				dcQueue.drain().transform(_ => scala.util.Failure(err))
			case Success((mainWritten, stale)) =>
				if (dcQueue.pending > 0) log.info(
					s"Main pass done, wrote $mainWritten triples; waiting for the DataCite queue (${dcQueue.pending} subjects pending)"
				)
				dcQueue.drain().flatMap { queueWritten =>
					Future {
						val pruned = reconciler.remove(stale.toSeq)
						val written = mainWritten + queueWritten + pruned.mutatedTriples
						val skipped =
							if (dcQueue.failed + dcQueue.dropped == 0) ""
							else s" (${dcQueue.failed} subjects skipped on DataCite failures, ${dcQueue.dropped} dropped from the queue)"
						log.info(
							f"Citation materialization finished in $mode mode, changed $written triples " +
							f"($queueWritten via the DataCite queue, ${pruned.removed} stale subjects removed)$skipped " +
							f"in ${(System.nanoTime() - startNanos) / 1e9}%.0f s"
						)
						written
					}(using blockingEc)
				}
		}
	}

	private def listCitableSubjects(): IndexedSeq[IRI] = {
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  ?s a ?t .
			|  FILTER(?t IN (<${metaVocab.dataObjectClass}>, <${metaVocab.docObjectClass}>, <${metaVocab.collectionClass}>))
			|}""".stripMargin
		querySubjectsPaged(q)
	}

	/** Subjects with a completed biblio or citation triple. Licence-only DOI
	 *  subjects are deliberately retried after a failed DataCite lookup. */
	private def listMaterializedSubjects(): Set[IRI] = {
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  GRAPH <$derivedGraph> {
			|    ?s ?p ?o .
			|    FILTER(?p IN (<${metaVocab.hasBiblioInfo}>, <${metaVocab.hasCitationString}>))
			|  }
			|}""".stripMargin
		querySubjectsPaged(q).toSet
	}

	private def listDerivedSubjects(): Set[IRI] = {
		val q = s"""
			|SELECT DISTINCT ?s WHERE {
			|  GRAPH <$derivedGraph> { ?s ?p ?o }
			|}""".stripMargin
		querySubjectsPaged(q).toSet
	}

	/** Virtuoso may cap an otherwise successful result. The ordered subquery is its
	 *  scrollable-cursor pattern and keeps OFFSET outside MaxSortedTopRows. */
	private def querySubjectsPaged(query: String): IndexedSeq[IRI] = {
		val all = ArrayBuffer.empty[IRI]
		var offset = 0
		var more = true
		while (more) {
			val paged = s"SELECT ?s WHERE { { $query ORDER BY ?s } } LIMIT $SubjectPageSize OFFSET $offset"
			val page = querySubjects(paged)
			all ++= page
			offset += page.size
			more = page.nonEmpty
		}
		all.toIndexedSeq
	}

	private def querySubjects(query: String): IndexedSeq[IRI] = {
		val conn = repo.getConnection()
		try {
			val result = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
			val buf = ArrayBuffer.empty[IRI]
			try {
				while (result.hasNext()) {
					result.next().getValue("s") match {
						case iri: IRI => buf += iri
						case _ => ()
					}
				}
			}
			finally result.close()
			buf.toIndexedSeq
		}
		finally conn.close()
	}

	/** Per-batch context flowing through the pipeline stages. */
	private case class Batch(
		chunk: Seq[(IRI, Int)],
		idx: Int,
		statsBefore: StatementCache.Snapshot,
		prefetchMs: Long = 0,
		computeMs: Long = 0,
		deferred: Int = 0,
		desired: IndexedSeq[DesiredCitation] = IndexedSeq.empty
	)
	private case class PreparedSubject(
		desired: Option[DesiredCitation],
		deferred: Option[DataCiteQueue.Entry]
	)

	/**
	 * The main pass as a pipeline: prefetch (up to [[CitationMaterializer.PrefetchAhead]]
	 * batches in advance) -> compute (one batch at a time, internally parallelized into
	 * [[CitationMaterializer.ComputeParallelism]] slices) -> write (overlapping with the
	 * computation of subsequent batches).
	 */
	private def materializeInBatches(
		subjects: IndexedSeq[IRI],
		cache: StatementCache,
		cacheConn: CachingConnection,
		dcQueue: DataCiteQueue,
		mode: ReconcileMode,
		refreshedDois: java.util.Set[Doi]
	)(using ExecutionContext): Future[Int] = {
		val total = subjects.size
		val progress = new Progress(total, batchCount = (total + WriteBatchSize - 1) / WriteBatchSize)

		Source
			.fromIterator(() => subjects.zipWithIndex.grouped(WriteBatchSize).zipWithIndex)
			.mapAsync(PrefetchAhead) { (chunk, batchIdx) =>
				Future {
					val t0 = System.nanoTime()
					val statsBefore = cache.stats
					cache.prefetch(chunk.map(_._1))
					Batch(chunk, batchIdx, statsBefore, prefetchMs = (System.nanoTime() - t0) / 1000000)
				}(using blockingEc)
			}
			.mapAsync(1)(batch => computeBatch(batch, total, cacheConn, dcQueue, mode, refreshedDois))
			.mapAsync(WriteAhead) { batch =>
				Future {
					val t0 = System.nanoTime()
					val stats = reconciler.reconcile(batch.desired)
					progress.logBatch(batch, writeMs = (System.nanoTime() - t0) / 1000000, sparql = cache.stats.minus(batch.statsBefore))
					stats.mutatedTriples
				}(using blockingEc)
			}
			.runWith(Sink.fold(0)(_ + _))
	}

	/** Computes a batch's citation triples in parallel slices; deferred (DataCite-pending)
	 *  subjects are pushed to the queue in subject order after the slices join. */
	private def computeBatch(
		batch: Batch,
		total: Int,
		cacheConn: CachingConnection,
		dcQueue: DataCiteQueue,
		mode: ReconcileMode,
		refreshedDois: java.util.Set[Doi]
	)(using ExecutionContext): Future[Batch] = {
		val t0 = System.nanoTime()
		val sliceSize = math.max(1, (batch.chunk.size + ComputeParallelism - 1) / ComputeParallelism)
		val slices = batch.chunk.grouped(sliceSize).toIndexedSeq
		val existing =
			if (mode.reconcilesExisting) reconciler.loadExisting(batch.chunk.map(_._1))
			else Map.empty[IRI, ExistingCitation]

		Future
			.traverse(slices) { slice =>
				Future {
					val desired = ArrayBuffer.empty[DesiredCitation]
					val deferred = ArrayBuffer.empty[(DataCiteQueue.Entry, Int)]
					for ((subj, idx) <- slice)
						prepareSubject(
							subj, s"${idx + 1}/$total", cacheConn, mode,
							existing.get(subj), refreshedDois
						) match {
							case Some(prepared) =>
								prepared.desired.foreach(desired += _)
								prepared.deferred.foreach(entry => deferred += ((entry, idx)))
							case None => ()
						}
					(desired, deferred)
				}(using blockingEc)
			}
			.flatMap { results =>
				val entries = results.flatMap(_._2).sortBy(_._2).map(_._1)
				Future.sequence(entries.map(dcQueue.push)).map { _ =>
					batch.copy(
						computeMs = (System.nanoTime() - t0) / 1000000,
						deferred = results.map(_._2.size).sum,
						desired = results.flatMap(_._1).toIndexedSeq
					)
				}
			}
	}

	/** Computes all triplestore-dependent data once. DOI-dependent fields are
	 *  completed later by DataCiteQueue without rereading the subject. */
	private def prepareSubject(
		subj: IRI,
		tag: String,
		conn: CachingConnection,
		mode: ReconcileMode,
		existing: Option[ExistingCitation],
		refreshedDois: java.util.Set[Doi]
	): Option[PreparedSubject] =
		Try {
			subjectDoi(subj, conn) match {
				case None => PreparedSubject(Some(DesiredCitation(subj, triplesFor(subj, tag, conn))), None)
				case Some(doi) =>
					val refsBase = citer.getStructuralReferences(subj, conn).map(stripDataCite)
					val licence = refsBase.flatMap(_.licence).orElse(citer.getLicence(subj, conn))
					reuseDataCite(subj, doi, refsBase, licence, existing, mode) match {
						case Some(triples) =>
							PreparedSubject(Some(DesiredCitation(subj, triples)), None)
						case None =>
							if (mode.refreshesDataCite && refreshedDois.add(doi)) {
								citer.doiCiter.dropCache(doi)
							}
							log.debug(s"[$tag] Deferred $subj to the DataCite queue (DOI $doi)")
							PreparedSubject(None, Some(DataCiteQueue.Entry(subj, doi, refsBase, licence)))
					}
			}
		}
		match {
			case Success(prepared) => Some(prepared)
			case Failure(err) =>
				log.warning(s"[$tag] Skipping $subj: ${err.getMessage}")
				None
		}

	private def reuseDataCite(
		subj: IRI,
		doi: Doi,
		refsBase: Option[References],
		licence: Option[Licence],
		existing: Option[ExistingCitation],
		mode: ReconcileMode
	): Option[IndexedSeq[Statement]] =
		if (!mode.reconcilesExisting || mode.refreshesDataCite) None
		else
			refsBase match {
				case Some(base) =>
					existing.flatMap(_.references)
						.filter(refs =>
							refs.citationString.nonEmpty &&
							refs.citationBibTex.nonEmpty &&
							refs.citationRis.nonEmpty &&
							refs.doi.exists(_.doi == doi)
						)
						.map { stored =>
							triplesFromReferences(subj, base.copy(
								citationString = stored.citationString,
								citationBibTex = stored.citationBibTex,
								citationRis = stored.citationRis,
								doi = stored.doi
							))
						}
				case None =>
					existing.flatMap(_.citationString).map(citation =>
						citationAndLicence(subj, citation, licence)
					)
			}

	private def stripDataCite(refs: References): References = refs.copy(
		citationString = None, citationBibTex = None, citationRis = None, doi = None
	)

	private def writeDataCiteBatch(completed: Seq[DataCiteQueue.Completed]): Int = {
		val desired = completed.map { item =>
			import item.{bundle, entry}
			val triples = entry.refsBase match {
				case Some(base) =>
					val refs = base.copy(
						citationString = Some(bundle.html),
						citationBibTex = bundle.bibtex,
						citationRis = bundle.ris,
						doi = bundle.meta
					)
					triplesFromReferences(entry.subj, refs)
				case None =>
					citationAndLicence(entry.subj, bundle.html, entry.licence)
			}
			DesiredCitation(entry.subj, triples)
		}
		val stats = reconciler.reconcile(desired)
		log.info(s"DataCite queue: reconciled ${completed.size} subjects (${stats.mutatedTriples} changed triples)")
		stats.mutatedTriples
	}

	/** The first valid DOI of the subject, if any. */
	private def subjectDoi(subj: IRI, conn: CachingConnection): Option[Doi] =
		StatementSource.getStringValues(subj, metaVocab.hasDoi)(using conn)
			.flatMap(Doi.parse(_).toOption)
			.headOption

	private def triplesFor(subj: IRI, tag: String, conn: CachingConnection): IndexedSeq[Statement] = {
		val startNanos = System.nanoTime()
		log.debug(s"[$tag] Materializing citation for $subj ...")
		val out = ArrayBuffer.empty[Statement]

		val refs: Option[References] = citer.getReferences(subj, conn)
		val hasBiblio = biblioLiteral(refs).map { lit =>
			out += factory.createStatement(subj, metaVocab.hasBiblioInfo, lit)
		}.isDefined

		val citationOpt = refs.fold(citer.getCitation(subj, conn))(_.citationString)
		citationOpt.foreach { cit =>
			out += factory.createStatement(subj, metaVocab.hasCitationString, factory.createStringLiteral(cit))
		}

		val licenceOpt = refs.fold(citer.getLicence(subj, conn))(_.licence)
		licenceOpt.foreach { lic =>
			out += factory.createStatement(subj, metaVocab.dcterms.license, lic.url.toRdf)
		}

		val durMs = (System.nanoTime() - startNanos) / 1000000
		if (out.isEmpty) {
			log.info(s"[$tag] No citation triples produced for $subj (${durMs} ms)")
		}
		else {
			log.info(
				s"[$tag] Materialized ${out.size} triples for $subj in ${durMs} ms " +
				s"[biblio=$hasBiblio, citation=${citationOpt.isDefined}, licence=${licenceOpt.isDefined}]"
			)
		}
		out.toIndexedSeq
	}

	private def triplesFromReferences(subj: IRI, refs: References): IndexedSeq[Statement] = {
		val out = ArrayBuffer.empty[Statement]
		biblioLiteral(Some(refs)).foreach(lit =>
			out += factory.createStatement(subj, metaVocab.hasBiblioInfo, lit)
		)
		refs.citationString.foreach(cit =>
			out += factory.createStatement(subj, metaVocab.hasCitationString, factory.createStringLiteral(cit))
		)
		refs.licence.foreach(lic =>
			out += factory.createStatement(subj, metaVocab.dcterms.license, lic.url.toRdf)
		)
		out.toIndexedSeq
	}

	private def citationAndLicence(
		subj: IRI, citation: String, licence: Option[Licence]
	): IndexedSeq[Statement] =
		IndexedSeq(factory.createStatement(
			subj, metaVocab.hasCitationString, factory.createStringLiteral(citation)
		)) ++ licence.map(lic =>
			factory.createStatement(subj, metaVocab.dcterms.license, lic.url.toRdf)
		)

	private def biblioLiteral(refs: Option[References]): Option[Value] = {
		import spray.json.*
		import se.lu.nateko.cp.meta.core.data.JsonSupport.{given RootJsonFormat[References]}
		refs.map(js => factory.createStringLiteral(js.toJson.compactPrint))
	}

	/** Accumulates and logs main-pass progress; thread-safe (write stages may overlap). */
	private class Progress(total: Int, batchCount: Int) {
		private val startNanos = System.nanoTime()
		private var processed, written, deferred = 0

		def logBatch(batch: Batch, writeMs: Long, sparql: StatementCache.Snapshot): Unit = synchronized {
			processed += batch.chunk.size
			written += batch.desired.iterator.map(_.triples.size).sum
			deferred += batch.deferred
			val elapsedS = (System.nanoTime() - startNanos) / 1e9
			val rate = if (elapsedS > 0) processed / elapsedS else 0.0
			val etaS = if (rate > 0) (total - processed) / rate else 0.0
			log.info(
				f"Citation materialization progress: batch ${batch.idx + 1}/$batchCount " +
				f"(prefetch ${batch.prefetchMs} ms, compute ${batch.computeMs} ms, write ${writeMs} ms), " +
				f"$processed/$total subjects processed (${100.0 * processed / total}%.1f%%), " +
				f"$written triples written so far, $deferred deferred to the DataCite queue, " +
				f"${elapsedS}%.0f s elapsed, ${rate}%.1f subj/s, ETA ${etaS}%.0f s; sparql: $sparql"
			)
		}
	}

}

object CitationMaterializer {
	val WriteBatchSize = 1000
	val SubjectPageSize = 100_000

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

}
