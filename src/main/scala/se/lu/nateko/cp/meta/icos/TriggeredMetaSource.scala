package se.lu.nateko.cp.meta.icos

import scala.concurrent.duration.DurationInt

import akka.event.LoggingAdapter
import akka.actor.ActorRef
import se.lu.nateko.cp.meta.utils.Validated
import akka.stream.scaladsl.Source
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Keep
import akka.stream.ThrottleMode
import akka.actor.Status

abstract class TriggeredMetaSource[T <: TC : TcConf] extends TcMetaSource[T] {
	def log: LoggingAdapter
	def readState: Validated[State]

	protected def registerListener(actor: ActorRef): Unit

	override def state: Source[State, () => Unit] = Source
		.actorRef(PartialFunction.empty, PartialFunction.empty, 1, OverflowStrategy.dropHead)
		.mapMaterializedValue{actor =>
			registerListener(actor)
			() => actor ! Status.Success
		}
		.prepend(Source.single(2)) //triggering initial state reading at the stream startup
		.conflate(Keep.right) //swallow throttle's back-pressure
		.throttle(2, 1.minute, 1, _ => 2, ThrottleMode.Shaping) //2 units of cost per minute
		.mapConcat[State]{_ =>
			val stateV = Validated(readState).flatMap(identity)

			if(!stateV.errors.isEmpty){
				val errKind = if(stateV.result.isEmpty) "Hard error" else "Problems"
				val tcName = implicitly[TcConf[T]].tcPrefix
				log.warning(s"$errKind while reading $tcName metadata:\n${stateV.errors.mkString("\n")}")
			}
			stateV.result.toList
		}

}
