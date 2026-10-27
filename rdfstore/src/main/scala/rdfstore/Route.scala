package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Directive1, Route}
import org.eclipse.rdf4j.query.{MalformedQueryException, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.SparqlServerConfig
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlServer}
import se.lu.nateko.cp.meta.rdfstore.DerivedMetadataJsonProtocol.given
import se.lu.nateko.cp.meta.routes.SparqlRoute
import se.lu.nateko.cp.meta.routes.SparqlRoute.entityStrictifyTimeout
import se.lu.nateko.cp.meta.utils.rdf4j.transact

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** The HTTP API of the standalone RDF store: the public SPARQL endpoint, and the internal endpoints used by meta */
object Route:

	def apply(
		repo: Repository,
		/** Serves queries on the stored statements only, without the SPARQL magic index or derived metadata */
		vanillaSparql: SparqlServer,
		sparqlConf: SparqlServerConfig,
		derivedMetadata: DerivedMetadataService,
		makeReadonly: String => Future[String]
	)(using
		ActorSystem,
		ToResponseMarshaller[SparqlQuery]
	): Route =
		def datasetFromQueryParameters: Directive1[SparqlDataset] = extractRequest.map: request =>
			val params = request.uri.query()
			SparqlDataset(
				defaultGraphs = params.getAll("default-graph-uri"),
				namedGraphs = params.getAll("named-graph-uri")
			)

		def executeUnloggedUpdate(update: String): Route =
			repo.transact(conn =>
				conn.prepareUpdate(QueryLanguage.SPARQL, update).execute()
			) match
				case Success(_) => complete(StatusCodes.NoContent)
				case Failure(err: MalformedQueryException) => complete(StatusCodes.BadRequest -> err.getMessage)
				case Failure(err) => complete(StatusCodes.InternalServerError -> err.getMessage)

		val rejectUpdate: String => Route = _ =>
			complete(StatusCodes.MethodNotAllowed -> "The vanilla repository is read-only, updates go to /internal/sparql")

		def internalSparqlRoute(update: String => Route)(using ToResponseMarshaller[SparqlQuery]): Route =
			def runQuery(query: String, dataset: SparqlDataset): Route =
				complete(SparqlQuery(query, Quota.Unlimited, dataset))

			get:
				datasetFromQueryParameters: dataset =>
					parameter("query")(runQuery(_, dataset))
			~ post:
				toStrictEntity(entityStrictifyTimeout):
					formFields("query", "default-graph-uri".repeated, "named-graph-uri".repeated):
						(query, defaultGraphs, namedGraphs) =>
							runQuery(query, SparqlDataset(defaultGraphs.toSeq, namedGraphs.toSeq))
					~
					formField("update")(update) ~
					extractRequest: request =>
						datasetFromQueryParameters: dataset =>
							entity(as[String]): body =>
								if request.entity.contentType.mediaType.subType == "sparql-update"
								then update(body)
								else runQuery(body, dataset)

		SparqlRoute(sparqlConf)
		~ path("internal" / "sparql"):
			internalSparqlRoute(executeUnloggedUpdate)
		~ path("internal" / "vanilla" / "sparql"):
			internalSparqlRoute(rejectUpdate)(using vanillaSparql.marshaller)
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
		~ path("admin" / "read-only"):
			post:
				entity(as[String]) { message => complete(makeReadonly(message)) }
		~ pathEndOrSingleSlash:
			complete(StatusCodes.NotFound)
