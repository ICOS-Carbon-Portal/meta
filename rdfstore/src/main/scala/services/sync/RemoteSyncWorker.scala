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
import scala.concurrent.duration.DurationInt
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
		log.info("Starting synchronization of {} from {}", kinds.mkString(", "), config.endpoint)
		val remoteRepo = SPARQLRepository(config.endpoint.toString)
		remoteRepo.init()
		var lastProgress: Option[SyncProgress] = None

		val throttle = QueryThrottle(config.queryPauseFactor, config.minQueryPauseMillis.millis)
		RemoteMetaSync(local, Rdf4jSparqlRunner(remoteRepo), config.prune, throttle)
			.run(kinds)
			.via(killSwitch.flow)
			.runWith(Sink.foreach: progress =>
				lastProgress.filter(_.kind != progress.kind).foreach(done => log.info("Synchronized {}", done))
				lastProgress = Some(progress)
				if progress.batches > 0 && progress.batches % LogEveryBatches == 0 then
					log.info("Synchronization progress: {}", progress)
			)
			.onComplete: res =>
				remoteRepo.shutDown()
				res match
					case Success(_) =>
						if closed then log.info("Synchronization from {} stopped", config.endpoint)
						else lastProgress.foreach(done => log.info("Synchronized {}", done))
					case Failure(err) =>
						log.error(err, "Synchronization from {} failed", config.endpoint)
				if !closed then
					nextPass = Some(system.scheduler.scheduleOnce(config.pauseBetweenRunsMinutes.minutes)(runPass()))

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
