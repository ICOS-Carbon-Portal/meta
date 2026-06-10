package se.lu.nateko.cp.meta.citations

import scala.language.unsafeNulls

import akka.actor.{ActorSystem, Cancellable}
import akka.event.Logging
import akka.stream.Materializer
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.citation.CitationClient.{CitationCache, DoiCache}
import se.lu.nateko.cp.meta.services.citation.{CitationMaterializer, CitationProvider}
import se.lu.nateko.cp.meta.services.sparql.VirtuosoRepository

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Continuously running service that keeps the materialised citation triples in
 * the Virtuoso triplestore up to date.
 *
 * It builds the same [[CitationProvider]] / [[CitationMaterializer]] the meta
 * service used to wire in, runs `materializeAll()` shortly after startup, and
 * then re-runs it on a fixed delay. Each run is allowed to finish before the
 * next is scheduled, so runs never overlap; a failed run is logged and retried
 * on the next tick rather than crashing the service.
 */
class CitationMaterializationService(
	config: CpmetaConfig,
	citCache: CitationCache,
	doiCache: DoiCache
)(using system: ActorSystem, mat: Materializer, envriConfigs: EnvriConfigs):

	private given ExecutionContext = system.dispatcher
	private val log = Logging.getLogger(system, this)

	private val repo = new VirtuosoRepository(config.virtuoso)
	private val citer = CitationProvider(repo, citCache, doiCache, config)
	private val materializer = new CitationMaterializer(repo, citer, config.citations.derivedCitationsGraph)

	private val interval: FiniteDuration = config.citations.materializeIntervalMinutes.minutes
	// Give the citation cache a head start before the first full run, mirroring
	// the warm-up delay the meta service used to apply.
	private val initialDelay: FiniteDuration = if config.citations.eagerWarmUp then 60.seconds else 5.seconds

	@volatile private var scheduled: Option[Cancellable] = None
	@volatile private var stopped = false

	def start(): Unit =
		log.info(
			s"Citation materialization service starting " +
			s"(interval $interval, derived graph ${config.citations.derivedCitationsGraph})"
		)
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

	def stop(): Future[Unit] = synchronized:
		stopped = true
		scheduled.foreach(_.cancel())
		Future(repo.shutDown())

end CitationMaterializationService
