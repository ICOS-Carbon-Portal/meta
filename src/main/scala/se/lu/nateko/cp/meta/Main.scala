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
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.utils.sesame._
import org.openrdf.rio.RDFFormat
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer

object Main extends App with SimpleRoutingApp {

	implicit val system = ActorSystem("cpmeta")
	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val manager = OWLManager.createOWLOntologyManager
	val owl = utils.owlapi.getOntologyFromJarResourceFile("/owl/cpmeta.owl", manager)
	val onto = new Onto(owl)

	val ontUri = "http://meta.icos-cp.eu/ontologies/cpmeta/contentexamples/"
	val repo = Loading.fromResource("/owl/content_examples.owl", ontUri)
	val instServer = new SesameInstanceServer(repo, ontUri)
	val instOnto = new InstOnto(instServer, onto)

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
					pathSuffix("getExposedClasses"){
						complete(onto.getExposedClasses)
					} ~
					pathSuffix("getTopLevelClasses"){
						complete(onto.getTopLevelClasses)
					} ~
					pathSuffix("listIndividuals"){
						parameter('classUri){ uriStr =>
							complete(instOnto.getIndividuals(new URI(uriStr)))
						}
					} ~
					pathSuffix("getIndividual"){
						parameter('uri){ uriStr =>
							complete(instOnto.getIndividual(new URI(uriStr)))
						}
					}
				} ~
				pathEndOrSingleSlash{
					complete(fromResource("/www/index.html", MediaTypes.`text/html`))
				} ~
				pathSuffix("bundle.js"){
					complete(fromResource("/www/bundle.js", MediaTypes.`application/javascript`))
				} ~
				pathPrefix("ontologies" / "cpmeta"){
					pathSingleSlash{
						complete(fromResource("/owl/cpmeta.owl", MediaTypes.`text/plain`))
					} ~
					pathPrefix("contentexamples"){
						pathSingleSlash{
							complete(fromResource("/owl/content_examples.owl", MediaTypes.`text/plain`))
						}
					}
				}
			} ~
			post{
				pathPrefix("api"){
					pathSuffix("update"){
						entity(as[UpdateDto])(update => {
							instOnto.performUpdate(update)
							complete(StatusCodes.OK)
						})
					}
				}
			}
		}
	}

}
