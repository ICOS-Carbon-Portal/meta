package se.lu.nateko.cp.meta.keyword

import akka.actor.{Actor, ActorLogging, Props, Timers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
 * Continuous worker that periodically re-materializes the derived `hasKeyword`
 * triples. It runs once immediately on start and then on a fixed interval.
 *
 * Runs are non-overlapping: while a materialization is in progress, ticks that
 * fire in the meantime are ignored (the actor switches to a `busy` behaviour).
 * Failures are logged and do not stop the worker — the next tick retries.
 */
object KeywordMaterializationWorker:

	def props(materializer: KeywordMaterializer, interval: FiniteDuration)(using ExecutionContext): Props =
		Props(new KeywordMaterializationWorker(materializer, interval))

	private case object Tick
	private final case class Finished(written: Int)
	private final case class Failed(error: Throwable)

	private val TimerKey = "keyword-materialization-refresh"

class KeywordMaterializationWorker(
	materializer: KeywordMaterializer,
	interval: FiniteDuration
)(using ExecutionContext) extends Actor with Timers with ActorLogging:

	import KeywordMaterializationWorker.*

	override def preStart(): Unit =
		self ! Tick // run once immediately on startup
		timers.startTimerWithFixedDelay(TimerKey, Tick, interval)

	override def receive: Receive = idle

	private def idle: Receive =
		case Tick =>
			context.become(busy)
			materializer.materializeAll().onComplete:
				case Success(written) => self ! Finished(written)
				case Failure(error)   => self ! Failed(error)

	private def busy: Receive =
		case Tick =>
			log.warning("Previous keyword materialization is still running; skipping this tick")
		case Finished(written) =>
			log.info(s"Keyword materialization cycle done, $written triples in derived graph")
			context.become(idle)
		case Failed(error) =>
			log.error(error, "Keyword materialization cycle failed; will retry on next tick")
			context.become(idle)

end KeywordMaterializationWorker
