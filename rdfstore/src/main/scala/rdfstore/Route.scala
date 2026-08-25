package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import org.eclipse.rdf4j.query.{MalformedQueryException, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.services.derived.{DerivedMetadataRequest, DerivedMetadataService}
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataJsonProtocol.given
import se.lu.nateko.cp.meta.utils.rdf4j.transact

import scala.util.{Failure, Success}

/** SPARQL 1.1 query and update protocol surface owned by the RDF-store process. */
object Route:

	def apply(
		repo: Repository,
		sparqlConf: SparqlServerConfig,
		derivedMetadata: DerivedMetadataService
	)(using
		ActorSystem,
		ToResponseMarshaller[SparqlRequest]
	): Route =
		def executeUnloggedUpdate(update: String): Route =
			repo.transact(conn =>
				conn.prepareUpdate(QueryLanguage.SPARQL, update).execute()
			) match
				case Success(_) => complete(StatusCodes.NoContent)
				case Failure(err: MalformedQueryException) => complete(StatusCodes.BadRequest -> err.getMessage)
				case Failure(err) => complete(StatusCodes.InternalServerError -> err.getMessage)

		val internalSparqlRoute: Route =
			get:
				parameter("query")(query => complete(SparqlRequest(query, Quota.Unlimited)))
			~ post:
				formField("query")(query => complete(SparqlRequest(query, Quota.Unlimited))) ~
				formField("update")(executeUnloggedUpdate) ~
				extractRequest: request =>
					entity(as[String]): body =>
						if request.entity.contentType.mediaType.subType == "sparql-update"
						then executeUnloggedUpdate(body)
						else complete(SparqlRequest(body, Quota.Unlimited))

		SparqlRoute(sparqlConf)
		~ path("internal" / "sparql"):
			internalSparqlRoute
		~ path("internal" / "derived" / "v1" / "resolve"):
			post:
				entity(as[DerivedMetadataRequest]): request =>
					complete(derivedMetadata.resolve(request.resources))
		~ path("internal" / "derived" / "v1" / "drop-cache" / Remaining): doi =>
			post:
				if derivedMetadata.dropDoiCache(doi) then complete(StatusCodes.NoContent)
				else complete(StatusCodes.BadRequest -> s"Invalid DOI: $doi")
		~ path("health"):
			get:
				complete(StatusCodes.OK -> "ok")
		~ pathEndOrSingleSlash:
			complete(StatusCodes.NotFound)
