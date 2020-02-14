package se.lu.nateko.cp.meta.utils.async

import scala.concurrent.duration._
import akka.actor.Scheduler
import scala.concurrent.ExecutionContext

class CancellableAction(delay: FiniteDuration, scheduler: Scheduler)(action: => Unit)(implicit exe: ExecutionContext){

	private var itHappened: Boolean = false

	private val cancel = scheduler.scheduleOnce(delay, () => {itHappened = true;action})
	private val deadline = delay.fromNow

	def cancelOr(tooLateAction: => Unit): Unit = {
		val isTooLate = deadline.isOverdue()
		if(!isTooLate) cancel.cancel()
		if(isTooLate || itHappened) tooLateAction
	}
}
