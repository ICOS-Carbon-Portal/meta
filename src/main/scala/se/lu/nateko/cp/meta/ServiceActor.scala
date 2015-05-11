package se.lu.nateko.cp.meta

import akka.actor._
import spray.can.Http
import spray.http._
import HttpMethods._
import spray.json._
import DefaultJsonProtocol._

class ServiceActor extends Actor with ActorLogging {

	def receive = {

		case HttpRequest(GET, Uri.Path(path), _, _, _) =>
			sender ! HttpResponse(status = 404, entity = "Unknown resource!")

		case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")

		// when a new connection comes in we register ourselves as the connection handler
		case _: Http.Connected => sender ! Http.Register(self)

		case Timedout(HttpRequest(method, uri, _, _, _)) => sender ! HttpResponse(
				status = 500,
				entity = s"The $method request to '$uri' has timed out."
			)
	}


}