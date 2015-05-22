package se.lu.nateko.cp.meta

import CpmetaJsonProtocol._
import akka.actor.ActorSystem
import akka.pattern.ask
import spray.http.StatusCodes
import spray.routing.ExceptionHandler
import spray.routing.SimpleRoutingApp
import spray.can.Http
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import spray.http.HttpResponse
import spray.http.ContentType
import spray.http.HttpEntity
import spray.http.ContentTypes
import spray.http.MediaTypes
import spray.http.HttpCharsets
import spray.http.MediaType
import java.net.URI
import org.semanticweb.owlapi.apibinding.OWLManager

object Main extends App with SimpleRoutingApp {

	implicit val system = ActorSystem("cpmeta")
	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val manager = OWLManager.createOWLOntologyManager
	val onto = new Onto("/owl/cpmeta.owl", manager)
	val instOnto = new InstOnto("/owl/content_examples.owl", onto, manager)

	val exceptionHandler = ExceptionHandler{
		case ex =>
			val traceWriter = new java.io.StringWriter()
			ex.printStackTrace(new java.io.PrintWriter(traceWriter))
			val trace = traceWriter.toString
			val msg = if(ex.getMessage == null) "" else ex.getMessage
			complete((StatusCodes.InternalServerError, s"$msg\n$trace"))
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
					} ~
					pathSuffix("listAllTopClasses"){
						complete(onto.getTopLevelClasses)
					} ~
					pathSuffix("listIndividuals"){
						parameter('classUri){ uriStr =>
							complete(instOnto.listInstances(new URI(uriStr)))
						}
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
