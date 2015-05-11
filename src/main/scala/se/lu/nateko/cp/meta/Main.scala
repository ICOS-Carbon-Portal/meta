package se.lu.nateko.cp.meta

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.io.IO
import akka.util.Timeout

import scala.concurrent.duration._

import spray.can.Http


object Main extends App {

	implicit val system = ActorSystem("cpauth")
	implicit val timeout = Timeout(5 seconds)
	implicit val dispatcher = system.dispatcher

	val handler = system.actorOf(Props(new ServiceActor), name = "handler")
	
	IO(Http).ask(Http.Bind(handler, interface = "localhost", port = 9011))
		.onSuccess{ case _ =>
			sys.addShutdownHook{
				akka.io.IO(Http) ! akka.actor.PoisonPill
				Thread.sleep(1000)
				system.shutdown()
			}
		}

}
