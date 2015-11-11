package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

import se.lu.nateko.cp.meta.services.SparqlSelect

object SparqlRoute {

	val setSparqlHeaders = respondWithHeaders(
		`Access-Control-Allow-Origin`.*,
		`Cache-Control`(CacheDirectives.`no-cache`, CacheDirectives.`no-store`, CacheDirectives.`must-revalidate`)
	)

	def apply(implicit marsh: ToResponseMarshaller[SparqlSelect]): Route = {

		def makeResponse(query: String): Route = setSparqlHeaders {
			handleExceptions(MainRoute.exceptionHandler){
				encodeResponse(complete(SparqlSelect(query)))
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
