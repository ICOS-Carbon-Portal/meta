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
import spray.http.HttpResponse
import spray.http.ContentType
import spray.http.HttpEntity
import spray.http.ContentTypes
import spray.http.MediaTypes
import spray.http.HttpCharsets
import spray.http.MediaType

object Main extends App with SimpleRoutingApp {

	implicit val system = ActorSystem("cpmeta")
	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val onto = new Onto("/owl/cpmeta.owl")

	val exceptionHandler = ExceptionHandler{
		case ex => complete((StatusCodes.InternalServerError, ex.getMessage + "\n" + ex.getStackTrace))
	}

	def fromResource(path: String, mediaType: MediaType): HttpResponse = {
		val is = getClass.getResourceAsStream(path)
		val bytes = org.apache.commons.io.IOUtils.toByteArray(is)
		val contType = ContentType(mediaType, HttpCharsets.`UTF-8`)
		HttpResponse(entity = HttpEntity(contType, bytes))
	}

	startServer(interface = "::0", port = 9094) {
		handleExceptions(exceptionHandler){
			get{
				pathPrefix("api"){
					pathSuffix("listClasses"){
						complete(onto.getExposedClasses)
					}
				} ~
				pathEndOrSingleSlash{
					complete(fromResource("/www/index.html", MediaTypes.`text/html`))
				} ~
				pathSuffix("bundle.js"){
					complete(fromResource("/www/bundle.js", MediaTypes.`application/javascript`))
				}
			}
		}
	}

}
