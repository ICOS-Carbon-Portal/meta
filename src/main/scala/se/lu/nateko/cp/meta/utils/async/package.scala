package se.lu.nateko.cp.meta.utils

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

import akka.actor.Scheduler
import akka.Done

package object async {

	def ok: Future[Done] = Future.successful(Done)

	def error[T](msg: String): Future[T] = Future.failed(new Exception(msg))

	def timeLimit[T](
		future: Future[T],
		duration: FiniteDuration,
		using: Scheduler
	)(implicit ec: ExecutionContext): Future[T] = if(future.isCompleted) future else {

		val p = Promise[T]()

		using.scheduleOnce(duration){

			p.failure(new TimeoutException(s"Future timed out after $duration"))

		}

		Future.firstCompletedOf(Iterable(future, p.future))
	}

	def throttle(
		action: () => Unit,
		delay: FiniteDuration,
		using: Scheduler
	)(implicit ec: ExecutionContext): () => Unit = {
		val ongoing = new AtomicBoolean(false)

		() => {
			val wasOngoing = ongoing.getAndSet(true)
			if(!wasOngoing) using.scheduleOnce(delay){
				action()
				ongoing.set(false)
			}
		}
	}

	def executeSequentially[T](on: Iterable[T])(thunk: T => Future[Done])(implicit ctxt: ExecutionContext): Future[Done] =
		on.foldLeft(ok){(acc, next) => acc.flatMap(_ => thunk(next))}
}
