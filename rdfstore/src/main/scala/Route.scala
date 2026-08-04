package se.lu.nateko.cp.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import org.eclipse.rdf4j.query.{MalformedQueryException, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.utils.rdf4j.transact

import scala.util.{Failure, Success}

/** SPARQL 1.1 query and update protocol surface owned by the RDF-store process. */
object Route:

	def apply(repo: Repository)(using
		ActorSystem,
		ToResponseMarshaller[SparqlQuery]
	): Route =
		val queryRoute: Route =
			get {
				parameter("query") { query => complete(SparqlQuery(query)) }
			} ~ post {
				formField("query") { query => complete(SparqlQuery(query)) } ~
				entity(as[String]) { query => complete(SparqlQuery(query)) }
			}

		def executeUpdate(update: String): Route =
			repo.transact(conn =>
				conn.prepareUpdate(QueryLanguage.SPARQL, update).execute()
			) match
				case Success(_) => complete(StatusCodes.NoContent)
				case Failure(err: MalformedQueryException) => complete(StatusCodes.BadRequest -> err.getMessage)
				case Failure(err) => complete(StatusCodes.InternalServerError -> err.getMessage)

		val updateRoute: Route = post:
			formField("update")(executeUpdate) ~ entity(as[String])(executeUpdate)

		path("sparql"):
			queryRoute
		~ path("update"):
			updateRoute
		~ path("health"):
			get:
				complete(StatusCodes.OK -> "ok")
		~ pathEndOrSingleSlash:
			complete(StatusCodes.NotFound)
