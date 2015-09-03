package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._

object StaticRoute {

	def fromResource(path: String, mediaType: MediaType): HttpResponse = {
		val is = getClass.getResourceAsStream(path)
		val bytes = org.apache.commons.io.IOUtils.toByteArray(is)
		val contType = ContentType(mediaType, HttpCharsets.`UTF-8`)
		HttpResponse(entity = HttpEntity(contType, bytes))
	}

	def apply(config: CpmetaConfig): Route = get{
		path("edit" / Segment){ontId =>
			if(config.onto.contains(ontId)){
				pathEndOrSingleSlash{
					complete(fromResource("/www/index.html", MediaTypes.`text/html`))
				} ~
				pathSuffix("bundle.js"){
					complete(fromResource("/www/bundle.js", MediaTypes.`application/javascript`))
				}
			} else
				complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
		} ~
		path("ontologies" / Segment){ ontId =>
			config.onto.get(ontId) match{
				case None =>
					complete(StatusCodes.NotFound)
				case Some(ontConf) => 
					complete(fromResource(ontConf.owlResource, MediaTypes.`text/plain`))
			}
		}
	}

}