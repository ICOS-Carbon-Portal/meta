package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy, QueueOfferResult}
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.core.data.References

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Concurrent queue for citation subjects whose materialization requires DataCite lookups.
 *
 * Most citable subjects have no DOI and need no DataCite data at all. DOI subjects
 * arrive with their structural references already prepared, so this queue only
 * performs external lookups and writes the completed fields. A bounded number are completed
 * concurrently, then materialized in small batches via the
 * `writeBatch` callback. Entries whose DataCite lookups failed are skipped — and
 * thus retried on the next materialization run — rather than having error
 * placeholders materialized into the triplestore.
 */
class DataCiteQueue(
	doiCiter: CitationClient,
	writeBatch: Seq[DataCiteQueue.Completed] => Int
)(using system: ActorSystem) {
	import DataCiteQueue.*
	import system.dispatcher

	private val log = Logging.getLogger(system, this)
	private given Materializer = Materializer.matFromSystem(using system)
	private val blockingEc = system.dispatchers.lookup(CitationMaterializer.BlockingDispatcher)

	private val pushedCount, droppedCount, failedCount, processedCount = AtomicInteger(0)

	private val (queue, drained) = Source
		.queue[Entry](BufferSize, OverflowStrategy.backpressure, maxConcurrentOffers = BufferSize)
		.mapAsyncUnordered(FetchConcurrency) { entry =>
			dataCiteLookups(entry).map(entry -> _)
		}
		.groupedWithin(WriteBatchSize, WriteBatchMaxDelay)
		.mapAsync(1) { batch =>
			Future {
				val (ok, failed) = batch.partition(_._2.isDefined)
				failedCount.addAndGet(failed.size)
				for ((entry, _) <- failed)
					log.warning(
						s"DataCite lookups failed for DOI ${entry.doi}, skipping ${entry.subj} (will be retried on the next run)"
					)
				val completed = ok.flatMap((entry, bundle) => bundle.map(Completed(entry, _)))
				val written = if completed.isEmpty then 0 else writeBatch(completed)
				processedCount.addAndGet(batch.size)
				written
			}(using blockingEc)
		}
		.toMat(Sink.fold(0)(_ + _))(Keep.both)
		.run()

	/** Enqueues a subject, applying backpressure instead of silently dropping it. */
	def push(entry: Entry): Future[Unit] = {
		pushedCount.incrementAndGet()
		queue.offer(entry).map {
			case QueueOfferResult.Enqueued => ()
			case other =>
				droppedCount.incrementAndGet()
				throw new Exception(s"DataCite queue did not accept ${entry.subj} ($other)")
		}
	}

	def pending: Int = pushedCount.get - droppedCount.get - processedCount.get
	def dropped: Int = droppedCount.get
	def failed: Int = failedCount.get

	/** Closes the queue; the returned future completes, with the total number of
	 *  triples written via the queue, once all accepted entries are processed. */
	def drain(): Future[Int] = {
		queue.complete()
		drained
	}

	/** Completes, with overall success status, when all DataCite lookups needed to
	 *  materialize a citation for the given DOI have completed. The lookups are
	 *  individually time-limited by [[CitationClientImpl]], so this cannot hang. */
	private def dataCiteLookups(entry: Entry): Future[Option[Bundle]] = {
		val html = doiCiter.getCitation(entry.doi, CitationStyle.HTML)
		val bundle =
			if entry.refsBase.isEmpty then html.map(cit => Bundle(cit, None, None, None))
			else for {
				cit <- html
				bibtex <- doiCiter.getCitation(entry.doi, CitationStyle.bibtex)
				ris <- doiCiter.getCitation(entry.doi, CitationStyle.ris)
				meta <- doiCiter.getDoiMeta(entry.doi)
			}
			yield Bundle(cit, Some(bibtex), Some(ris), Some(meta))
		bundle.map(Some(_)).recover {
			case _ => None
		}
	}

} // end DataCiteQueue

object DataCiteQueue {
	val BufferSize = 1 << 17
	val FetchConcurrency = 8
	val WriteBatchSize = 100
	val WriteBatchMaxDelay = 5.seconds

	/** The DataCite data used by citation materialization: References carries the
	 *  HTML, BibTeX and RIS citation strings plus the DOI metadata. */
	val NeededStyles = Seq(CitationStyle.HTML, CitationStyle.bibtex, CitationStyle.ris)

	final case class Entry(subj: IRI, doi: Doi, refsBase: Option[References])
	final case class Bundle(html: String, bibtex: Option[String], ris: Option[String], meta: Option[DoiMeta])
	final case class Completed(entry: Entry, bundle: Bundle)

} // end DataCiteQueue
