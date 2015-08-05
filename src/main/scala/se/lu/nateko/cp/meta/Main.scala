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
import org.openrdf.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import scala.util.Success
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer

object Main extends App with SimpleRoutingApp {

	implicit val system = ActorSystem("cpmeta")
	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val config: AppConfig = AppConfig.load.get

	val onto = {
		val manager = OWLManager.createOWLOntologyManager
		val owl = utils.owlapi.getOntologyFromJarResourceFile(config.schemaOwlFileResourcePath, manager)
		new Onto(owl)
	}

	val instOnto = {

//		val initRepo = config.instOwlFileResourcePath match{
//			case Some(resource) => Loading.fromResource(resource, config.instOntUri)
//			case None => Loading.empty
//		}

		val initRepo = Loading.empty
		val factory = initRepo.getValueFactory
		val context = factory.createURI(config.instOntUri)

		val log = PostgresRdfLog.fromConfig(config, factory)
		if(!log.isInitialized) log.initLog()

		val repoFut = RdfUpdateLogIngester.ingest(log.updates, initRepo, context)
		val repo = Await.result(repoFut, Duration.Inf)

		val sesameServer = new SesameInstanceServer(repo, context)
		val loggingServer = new LoggingInstanceServer(sesameServer, log)

		new InstOnto(loggingServer, onto)
	}

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
						complete(fromResource(config.schemaOwlFileResourcePath, MediaTypes.`text/plain`))
					} ~
					pathPrefix("contentexamples"){
						pathSingleSlash{
							config.instOwlFileResourcePath match{
								case Some(resource) => complete(fromResource(resource, MediaTypes.`text/plain`))
								case None => complete(StatusCodes.NotFound)
							}
							
						}
					}
				}
			} ~
			post{
				pathPrefix("api"){
					pathSuffix("applyupdates"){
						entity(as[Seq[UpdateDto]])(updates => {
							instOnto.applyUpdates(updates).get
							complete(StatusCodes.OK)
						})
					}
					pathSuffix("performreplacement"){
						entity(as[ReplaceDto])(replacement => {
							instOnto.performReplacement(replacement).get
							complete(StatusCodes.OK)
						})
					}
				}
			}
		}
	}

}
