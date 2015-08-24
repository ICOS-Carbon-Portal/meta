package se.lu.nateko.cp.meta

import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import java.net.URI


object Main extends App with CpmetaJsonProtocol{

	implicit val system = ActorSystem("cpmeta")
	implicit val materializer = ActorMaterializer(namePrefix = Some("cpmeta_mat"))

	implicit val dispatcher = system.dispatcher
	implicit val scheduler = system.scheduler

	val config: CpmetaConfig = ConfigLoader.getDefault
	val db: MetaDb = MetaDb(config)

	val exceptionHandler = ExceptionHandler{
		case ex =>
			val traceWriter = new java.io.StringWriter()
			ex.printStackTrace(new java.io.PrintWriter(traceWriter))
			val trace = traceWriter.toString
			val msg = if(ex.getMessage == null) "" else ex.getMessage
			complete((StatusCodes.InternalServerError, s"$msg\n$trace"))
	}

	val sparqlResMediaType = MediaType.custom(
		mainType = "application",
		subType = "sparql-results+json",
		encoding = MediaType.Encoding.Fixed(HttpCharsets.`UTF-8`),
		compressible = true,
		fileExtensions = ".srj" :: Nil
	)

	val allowAllOrigins = respondWithHeader(headers.`Access-Control-Allow-Origin`.*)

	def fromResource(path: String, mediaType: MediaType): HttpResponse = {
		val is = getClass.getResourceAsStream(path)
		val bytes = org.apache.commons.io.IOUtils.toByteArray(is)
		val contType = ContentType(mediaType, HttpCharsets.`UTF-8`)
		HttpResponse(entity = HttpEntity(contType, bytes))
	}

	val route = handleExceptions(exceptionHandler){
		get{
			pathPrefix("api"){
				pathSuffix("getExposedClasses"){
					complete(db.onto.getExposedClasses)
				} ~
				pathSuffix("getTopLevelClasses"){
					complete(db.onto.getTopLevelClasses)
				} ~
				pathSuffix("listIndividuals"){
					parameter('classUri){ uriStr =>
						complete(db.instOnto.getIndividuals(new URI(uriStr)))
					}
				} ~
				pathSuffix("getIndividual"){
					parameter('uri){ uriStr =>
						complete(db.instOnto.getIndividual(new URI(uriStr)))
					}
				}
			} ~
			pathEndOrSingleSlash{
				complete(fromResource("/www/index.html", MediaTypes.`text/html`))
			} ~
			pathSuffix("bundle.js"){
				complete(fromResource("/www/bundle.js", MediaTypes.`application/javascript`))
			} ~
			path("ontologies" / "cpmeta"){
				complete(fromResource(config.onto.owlResource, MediaTypes.`text/plain`))
			} ~
			pathPrefix("sparql"){
				parameter('query){ query =>
					val json = db.sparql.executeQuery(query)
					allowAllOrigins {
						encodeResponse{
							complete(HttpResponse(entity = HttpEntity(sparqlResMediaType, json)))
						}
					}
				}
			}
		} ~
		post{
			pathPrefix("api"){
				pathSuffix("applyupdates"){
					entity(as[Seq[UpdateDto]])(updates => {
						db.instOnto.applyUpdates(updates).get
						complete(StatusCodes.OK)
					})
				}
				pathSuffix("performreplacement"){
					entity(as[ReplaceDto])(replacement => {
						db.instOnto.performReplacement(replacement).get
						complete(StatusCodes.OK)
					})
				}
			}
		}
	}
	Http()
		.bindAndHandle(route, "localhost", 9094)
		.onSuccess{
			case binding =>
				sys.addShutdownHook{
					val doneFuture = binding.unbind().andThen{
						case _ => db.close(); system.shutdown()
					}
					Await.result(doneFuture, 3 seconds)
				}
				println(binding)
		}

}
