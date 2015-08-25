package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.sparqlserver.SparqlServer
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._

object SparqlRoute {

	val sparqlResMediaType = MediaType.custom(
		mainType = "application",
		subType = "sparql-results+json",
		encoding = MediaType.Encoding.Fixed(HttpCharsets.`UTF-8`),
		compressible = true,
		fileExtensions = ".srj" :: Nil
	)

	val allowAllOrigins = respondWithHeader(headers.`Access-Control-Allow-Origin`.*)

	def apply(server: SparqlServer): Route = get{
		pathPrefix("sparql"){
			parameter('query){ query =>
				val json = server.executeQuery(query)
				allowAllOrigins {
					encodeResponse{
						complete(HttpResponse(entity = HttpEntity(sparqlResMediaType, json)))
					}
				}
			}
		}
	}

}