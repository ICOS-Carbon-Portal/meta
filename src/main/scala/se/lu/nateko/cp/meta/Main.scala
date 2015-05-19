package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import akka.pattern.ask
import spray.http.StatusCodes
import spray.routing.ExceptionHandler
import spray.routing.SimpleRoutingApp
import spray.can.Http
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import CpmetaJsonProtocol._

object Main extends App with SimpleRoutingApp {

	implicit val system = ActorSystem("cpmeta")
	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val onto = new Onto("/owl/cpmeta.owl")

	val exceptionHandler = ExceptionHandler{
		case ex => complete((StatusCodes.InternalServerError, ex.getMessage + "\n" + ex.getStackTrace))
	}

	startServer(interface = "::0", port = 9094) {
		handleExceptions(exceptionHandler){
			get{
				path("listClasses"){
					complete(onto.getExposedClasses)
				}
			}
		}
	}

}
