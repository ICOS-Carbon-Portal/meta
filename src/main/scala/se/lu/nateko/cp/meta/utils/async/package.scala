package se.lu.nateko.cp.meta.utils

import java.util.concurrent.TimeoutException

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

import akka.actor.Scheduler

package object async {

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

}
