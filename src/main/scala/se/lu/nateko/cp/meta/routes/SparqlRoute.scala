package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import se.lu.nateko.cp.meta.api.SparqlQuery

object SparqlRoute {

	val setSparqlHeaders = respondWithHeaders(
		`Access-Control-Allow-Origin`.*,
		`Cache-Control`(CacheDirectives.`no-cache`, CacheDirectives.`no-store`, CacheDirectives.`must-revalidate`)
	)

	def apply()(implicit marsh: ToResponseMarshaller[SparqlQuery], mat: Materializer): Route = {

		val makeResponse: String => Route = query => setSparqlHeaders {
			handleExceptions(MainRoute.exceptionHandler){
				handleRejections(RejectionHandler.default){
					complete(SparqlQuery(query))
				}
			}
		}

		pathPrefix("sparql"){
			get{
				parameter('query)(makeResponse)
			} ~
			post{
				formField('query)(makeResponse) ~
				entity(as[String])(makeResponse)
			} ~
			options{
				respondWithHeaders(
					`Access-Control-Allow-Origin`.*,
					`Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST),
					`Access-Control-Allow-Headers`("Content-Type")
				){
					complete(StatusCodes.OK)
				}
			}
		}
	}

}
