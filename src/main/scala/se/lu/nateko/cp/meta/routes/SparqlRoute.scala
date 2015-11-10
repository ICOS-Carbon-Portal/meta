package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.services.SparqlServer
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import se.lu.nateko.cp.meta.services.SparqlSelect

object SparqlRoute {

	val setSparqlHeaders = respondWithHeaders(
		`Access-Control-Allow-Origin`.*,
		`Cache-Control`(CacheDirectives.`no-cache`, CacheDirectives.`no-store`, CacheDirectives.`must-revalidate`)
	)

	def apply(server: SparqlServer): Route = {
		implicit val marsh = server.marshaller

		def makeResponse(query: String): Route = setSparqlHeaders {
			handleExceptions(MainRoute.exceptionHandler){
				complete(SparqlSelect(query))
			}
		}

		pathPrefix("sparql"){
			get{
				parameter('query)(makeResponse)
			} ~
			post{
				formField("query")(makeResponse) ~
				entity(as[String])(makeResponse)
			}
		}
	}

}
