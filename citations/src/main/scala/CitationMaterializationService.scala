package se.lu.nateko.cp.meta.citations

import scala.language.unsafeNulls

import akka.actor.{ActorSystem, Cancellable}
import akka.event.Logging
import se.lu.nateko.cp.meta.CitationConfig
import se.lu.nateko.cp.meta.services.citation.CitationMaterializer

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Keeps the materialised citation triples in the Virtuoso triplestore up to
 * date: runs `materializeAll()` shortly after startup and then re-runs it on a
 * fixed delay. Each run finishes before the next is scheduled (no overlap); a
 * failed run is logged and retried on the next tick rather than crashing the
 * service.
 */
class CitationMaterializationService(
	materializer: CitationMaterializer,
	interval: FiniteDuration,
	initialDelay: FiniteDuration
)(using system: ActorSystem):

	private given ExecutionContext = system.dispatcher
	private val log = Logging.getLogger(system, this)

	@volatile private var scheduled: Option[Cancellable] = None
	@volatile private var stopped = false

	def start(): Unit =
		log.info(s"Citation materialization scheduler starting (interval $interval, first run in $initialDelay)")
		scheduleNext(initialDelay)

	private def scheduleNext(delay: FiniteDuration): Unit = synchronized:
		if !stopped then
			scheduled = Some(system.scheduler.scheduleOnce(delay)(runOnce()))

	private def runOnce(): Unit =
		materializer.materializeAll().onComplete:
			case Success(written) =>
				log.info(s"Citation materialization finished, wrote $written triples; next run in $interval")
				scheduleNext(interval)
			case Failure(err) =>
				log.error(err, s"Citation materialization failed; retrying in $interval")
				scheduleNext(interval)

	def stop(): Unit = synchronized:
		stopped = true
		scheduled.foreach(_.cancel())

end CitationMaterializationService

object CitationMaterializationService:
	def apply(materializer: CitationMaterializer, conf: CitationConfig)(using ActorSystem): CitationMaterializationService =
		val interval = conf.materializeIntervalMinutes.minutes
		// Give the citation cache a head start before the first full run.
		val initialDelay = 5.seconds
		new CitationMaterializationService(materializer, interval, initialDelay)
