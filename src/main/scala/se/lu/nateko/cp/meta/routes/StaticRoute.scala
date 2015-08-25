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
		pathEndOrSingleSlash{
			complete(fromResource("/www/index.html", MediaTypes.`text/html`))
		} ~
		pathSuffix("bundle.js"){
			complete(fromResource("/www/bundle.js", MediaTypes.`application/javascript`))
		} ~
		path("ontologies" / "cpmeta"){
			complete(fromResource(config.onto.owlResource, MediaTypes.`text/plain`))
		}
	}

}