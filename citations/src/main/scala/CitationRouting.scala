package se.lu.nateko.cp.meta.citations

import scala.language.unsafeNulls

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationProvider}

import spray.json.*

import java.net.URI
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * HTTP surface of the citations service.
 *
 *   - `GET  /citations/staticobject?uri=…`     freshly-computed StaticObject JSON
 *   - `GET  /citations/staticcollection?uri=…` freshly-computed StaticCollection JSON
 *   - `POST /citations/dropCache/<doi>`         invalidate a DOI's cached citation
 *   - `POST /citations/dumpCaches`              persist the in-memory caches to disk
 *
 * The two GET endpoints are what the meta service's DOI-minting path calls to
 * obtain non-stale citation metadata (see `RemoteCitationFetcher` in meta).
 */
class CitationRouting(citer: CitationProvider)(using ExecutionContext) {

	private def asJson(body: String): HttpEntity.Strict =
		HttpEntity(ContentTypes.`application/json`, body)

	val route: Route = pathPrefix("citations"){
		(get & path("staticobject") & parameter("uri")){ uriStr =>
			citer.fetchFreshObject(new URI(uriStr)) match {
				case Some(obj) => complete(asJson(obj.toJson.compactPrint))
				case None => complete(StatusCodes.NotFound -> s"No static object found for $uriStr")
			}
		} ~
		(get & path("staticcollection") & parameter("uri")){ uriStr =>
			citer.fetchFreshCollection(new URI(uriStr)) match {
				case Some(coll) => complete(asJson(coll.toJson.compactPrint))
				case None => complete(StatusCodes.NotFound -> s"No collection found for $uriStr")
			}
		} ~
		(post & path("dropCache" / Remaining)){ maybeDoi =>
			Doi.parse(maybeDoi) match {
				case Success(doi) =>
					citer.doiCiter.dropCache(doi)
					complete(StatusCodes.OK)
				case Failure(err) => complete(StatusCodes.BadRequest -> err.getMessage)
			}
		} ~
		(post & path("dumpCaches")){
			val dump = for {
				_ <- CitationClient.writeCitCache(citer.doiCiter)
				_ <- CitationClient.writeDoiCache(citer.doiCiter)
			}
			yield "Citation caches dumped to disk"
			onComplete(dump){
				case Success(msg) => complete(StatusCodes.OK -> msg)
				case Failure(err) => complete(StatusCodes.InternalServerError -> err.getMessage)
			}
		}
	}

} // end CitationRouting
