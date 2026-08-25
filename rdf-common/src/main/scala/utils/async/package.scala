package se.lu.nateko.cp.meta.utils.async

import akka.Done
import akka.actor.Scheduler

import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}

def ok: Future[Done] = Future.successful(Done)

def error[T](msg: String): Future[T] = Future.failed(new Exception(msg))

def timeLimit[T](
	future: Future[T],
	duration: FiniteDuration,
	using: Scheduler,
	errContext: String
)(using ExecutionContext): Future[T] = if(future.isCompleted) future else {

	val p = Promise[T]()
	future.onComplete(p.tryComplete)

	using.scheduleOnce(duration){

		p.tryFailure(new TimeoutException(s"Future timed out after $duration ($errContext)"))

	}

	p.future
}

def throttle(
	action: () => Unit,
	delay: FiniteDuration,
	scheduler: Scheduler
)(using ctxt: ExecutionContext): () => Unit = {
	val ongoing = new AtomicBoolean(false)

	() => {
		val wasOngoing = ongoing.getAndSet(true)
		if(!wasOngoing) scheduler.scheduleOnce(delay){
			try
				action()
			catch
				case err: Throwable => ctxt.reportFailure(err)
			finally
				ongoing.set(false)
		}
	}
}
