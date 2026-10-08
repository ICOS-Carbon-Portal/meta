package se.lu.nateko.cp.meta.services.sync

import scala.language.unsafeNulls

import akka.actor.{ActorSystem, Cancellable}
import akka.stream.KillSwitches
import akka.stream.scaladsl.Sink
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import se.lu.nateko.cp.meta.RemoteSyncConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success}

/**
 * Repeatedly runs RemoteMetaSync passes over the configured kinds of resources, pausing between the passes,
 * until closed.
 */
final class RemoteSyncWorker private (local: Repository, config: RemoteSyncConfig, kinds: Seq[SyncKind])(
	using system: ActorSystem, envriConfigs: EnvriConfigs
) extends AutoCloseable:
	import RemoteSyncWorker.LogEveryBatches
	private given ExecutionContext = system.dispatcher
	private val log = system.log

	private val killSwitch = KillSwitches.shared("remote-sync")
	@volatile private var closed = false
	@volatile private var nextPass: Option[Cancellable] = None

	override def close(): Unit =
		closed = true
		nextPass.foreach(_.cancel())
		killSwitch.shutdown()

	private def runPass(): Unit = if !closed then
		log.info(
			"Starting synchronization of {} from {} (prune = {}, remote query pause factor {}, at least {} ms)",
			Array[Any](SyncKind.syncOrder(kinds).mkString(", "), config.endpoint, config.prune, config.queryPauseFactor, config.minQueryPauseMillis)
		)
		val remoteRepo = SPARQLRepository(config.endpoint.toString)
		remoteRepo.init()
		val passStart = System.nanoTime
		var kindStart = passStart
		var lastProgress: Option[SyncProgress] = None
		var lastProgressTime = passStart
		def elapsed(since: Long) = Duration.fromNanos(System.nanoTime - since).toSeconds.toInt.seconds

		val throttle = QueryThrottle(config.queryPauseFactor, config.minQueryPauseMillis.millis)
		RemoteMetaSync(local, Rdf4jSparqlRunner(remoteRepo), config.prune, throttle)
			.run(kinds)
			.via(killSwitch.flow)
			.runWith(Sink.foreach: progress =>
				if !lastProgress.exists(_.kind == progress.kind) then
					// kinds are synchronized one after the other
					lastProgress.foreach: done =>
						log.info("Synchronized {} in {}", done, Duration.fromNanos(lastProgressTime - kindStart).toSeconds.toInt.seconds)
						kindStart = lastProgressTime
				lastProgress = Some(progress)
				lastProgressTime = System.nanoTime
				if progress.batches > 0 && progress.batches % LogEveryBatches == 0 then
					log.info("Synchronization progress after {}: {}", elapsed(kindStart), progress)
			)
			.onComplete: res =>
				remoteRepo.shutDown()
				res match
					case Success(_) =>
						if closed then log.info("Synchronization from {} stopped after {}", config.endpoint, elapsed(passStart))
						else
							lastProgress.foreach(done => log.info("Synchronized {} in {}", done, elapsed(kindStart)))
							log.info("Synchronization from {} completed in {}", config.endpoint, elapsed(passStart))
					case Failure(err) =>
						log.error(
							err, "Synchronization from {} failed after {}, at {}",
							config.endpoint, elapsed(passStart), lastProgress.fold("start")(_.toString)
						)
				if !closed then
					val pause = config.pauseBetweenRunsMinutes.minutes
					log.info("Next synchronization from {} in {}", config.endpoint, pause)
					nextPass = Some(system.scheduler.scheduleOnce(pause)(runPass()))

end RemoteSyncWorker

object RemoteSyncWorker:
	private val LogEveryBatches = 200

	/** Starts the worker if synchronization is enabled; fails on unknown kinds in the config */
	def start(local: Repository, config: RemoteSyncConfig)(using ActorSystem, EnvriConfigs): Option[RemoteSyncWorker] =
		val kindNames = config.kinds.getOrElse(Nil)
		val unknownKinds = kindNames.filter(SyncKind.parse(_).isEmpty)
		require(
			unknownKinds.isEmpty,
			s"Unknown remote sync kinds: ${unknownKinds.mkString(", ")}. Known kinds: ${SyncKind.values.mkString(", ")}"
		)
		val kinds = if kindNames.isEmpty then SyncKind.values.toSeq else kindNames.flatMap(SyncKind.parse)

		Option.when(config.enabled):
			val worker = new RemoteSyncWorker(local, config, kinds)
			worker.runPass()
			worker
