package se.lu.nateko.cp.meta.test.api

import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import scala.concurrent.Future

class AkkaTests extends AsyncFunSuite with BeforeAndAfterAll:
	val system = ActorSystem("akkaTests")
	override protected def afterAll(): Unit =
		system.terminate()

	test("Actor system dispatcher uses multiple threads"):
		import system.dispatcher
		val threadNameFuts = (1 to 10).map(_ => Future{
			Thread.sleep(2)
			Thread.currentThread.getName
		})
		Future.sequence(threadNameFuts).map: names =>
			//println(names)
			assert(names.distinct.size > 1)
