package se.lu.nateko.cp.meta.routes

import akka.stream.Materializer
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

	private def sparqlResponse(server: SparqlServer, query: String): Route = {
		allowAllOrigins {
			handleExceptions(MainRoute.exceptionHandler){
				val json = server.executeQuery(query)
				encodeResponse {
					complete(HttpResponse(entity = HttpEntity(sparqlResMediaType, json)))
				}
			}
		}
	}

	def apply(server: SparqlServer)(implicit mat: Materializer): Route = pathPrefix("sparql"){
		val makeResponse: String => Route = sparqlResponse(server, _)
		get{
			parameter('query)(makeResponse)
		} ~
		post{
			formField("query")(makeResponse) ~
			entity(as[String])(makeResponse)
		}
	}

}