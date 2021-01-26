package se.lu.nateko.cp.meta.upload

import scala.collection.mutable
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class PubSubBus {

	type Listener = PartialFunction[PubSubEvent, Unit]
	private val listeners = mutable.Buffer.empty[Listener]

	def publish(event: PubSubEvent): Unit = queue.execute{() =>
		listeners.foreach{pf =>
			pf.applyOrElse(event, (_: PubSubEvent) => ())
		}
	}

	def subscribe(listener: Listener): Unit = {
		listeners.append(listener)
	}
}
