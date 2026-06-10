package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.doi.Doi

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Success

/**
 * FIFO queue for citation subjects whose materialization requires DataCite lookups.
 *
 * Most citable subjects have no DOI and need no DataCite data at all; the
 * materializer's main pass handles those (and subjects whose DataCite data is
 * already cached) directly, and pushes the rest here so that DataCite's rate
 * limit does not stall the main stream. Entries are processed in push order,
 * concurrently with the main pass: for each entry the queue awaits completion of
 * all the DataCite lookups for its DOI (the lookups themselves were already
 * triggered when the entry was pushed, and are paced by [[CitationClientImpl]]'s
 * shared throttle), then materializes entries in small batches via the
 * `writeBatch` callback. Entries whose DataCite lookups failed are skipped — and
 * thus retried on the next materialization run — rather than having error
 * placeholders materialized into the triplestore.
 */
class DataCiteQueue(
	doiCiter: CitationClient,
	writeBatch: Seq[IRI] => Int
)(using system: ActorSystem):
	import DataCiteQueue.*
	import system.dispatcher

	private val log = Logging.getLogger(system, this)
	private given Materializer = Materializer.matFromSystem(using system)

	private val pushedCount, droppedCount, failedCount, processedCount = AtomicInteger(0)

	private case class Entry(subj: IRI, doi: Doi)

	private val (queue, drained) = Source
		.queue[Entry](BufferSize, OverflowStrategy.dropNew, maxConcurrentOffers = BufferSize)
		.mapAsync(1): entry =>
			dataCiteLookups(entry.doi).map(allOk => entry -> allOk)
		.groupedWithin(WriteBatchSize, WriteBatchMaxDelay)
		.map: batch =>
			val (ok, failed) = batch.partition(_._2)
			failedCount.addAndGet(failed.size)
			for (entry, _) <- failed do
				log.warning(
					s"DataCite lookups failed for DOI ${entry.doi}, skipping ${entry.subj} (will be retried on the next run)"
				)
			val written = if ok.isEmpty then 0 else writeBatch(ok.map(_._1.subj))
			processedCount.addAndGet(batch.size)
			written
		.toMat(Sink.fold(0)(_ + _))(Keep.both)
		.run()

	/** Enqueues a subject for materialization once the DataCite lookups for its DOI
	 *  complete. Non-blocking; if the buffer is full the subject is dropped and
	 *  stays unmaterialized, to be retried on the next run. */
	def push(subj: IRI, doi: Doi): Unit =
		pushedCount.incrementAndGet()
		queue.offer(Entry(subj, doi)).onComplete:
			case Success(QueueOfferResult.Enqueued) => ()
			case other =>
				droppedCount.incrementAndGet()
				log.warning(s"DataCite queue did not accept $subj ($other), it will be retried on the next run")

	def pending: Int = pushedCount.get - droppedCount.get - processedCount.get
	def dropped: Int = droppedCount.get
	def failed: Int = failedCount.get

	/** Closes the queue; the returned future completes, with the total number of
	 *  triples written via the queue, once all accepted entries are processed. */
	def drain(): Future[Int] =
		queue.complete()
		drained

	/** Completes, with overall success status, when all DataCite lookups needed to
	 *  materialize a citation for the given DOI have completed. The lookups are
	 *  individually time-limited by [[CitationClientImpl]], so this cannot hang. */
	private def dataCiteLookups(doi: Doi): Future[Boolean] =
		val lookups: Seq[Future[Any]] =
			NeededStyles.map(style => doiCiter.getCitation(doi, style)) :+ doiCiter.getDoiMeta(doi)
		Future
			.sequence(lookups.map(_.transform(res => Success(res.isSuccess))))
			.map(_.forall(identity))

end DataCiteQueue

object DataCiteQueue:
	val BufferSize = 1 << 17
	val WriteBatchSize = 100
	val WriteBatchMaxDelay = 5.seconds

	/** The DataCite data used by citation materialization: References carries the
	 *  HTML, BibTeX and RIS citation strings plus the DOI metadata. */
	val NeededStyles = Seq(CitationStyle.HTML, CitationStyle.bibtex, CitationStyle.ris)

end DataCiteQueue
