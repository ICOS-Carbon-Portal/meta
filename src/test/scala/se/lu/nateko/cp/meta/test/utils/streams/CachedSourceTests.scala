package se.lu.nateko.cp.meta.test.utils.streams

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Success

class CachedSourceTests extends AnyFunSuite with BeforeAndAfterAll{

	private given system: ActorSystem = ActorSystem("CachedSourceTests")
	import system.dispatcher

	override def afterAll(): Unit = {
		system.terminate()
	}

	test("SinkQueue behaviour test"){
		val sink = Sink.queue[Int]()
		val src = Source.queue[Int](2, OverflowStrategy.backpressure)
		val (qIn, qOut) = src.toMat(sink)(Keep.both).run()
		val f = for(o1 <- qOut.pull(); o2 <- qOut.pull()) yield o1 -> o2
		qIn.offer(1)
		qIn.offer(2)
		qIn.complete()
		f.onComplete{t =>
			assert(t === Success((Some(1),Some(2))))
			qOut.cancel()
			qOut.cancel()
		}
	}
}
