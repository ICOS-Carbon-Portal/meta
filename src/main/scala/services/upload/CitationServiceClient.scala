package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.utils.Validated

import spray.json.*

import java.net.URI
import java.net.URLEncoder
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import scala.util.Try

/**
 * meta's client for the standalone citations service. It is used to:
 *   - synchronously retrieve freshly-computed static objects/collections for the
 *     DOI-minting path (which must not work off the possibly-lagging materialized
 *     citation graph), and
 *   - forward `dropCache` requests, since the in-memory citation cache now lives
 *     in the citations service.
 */
class CitationServiceClient(serviceBaseUrl: URI) extends StaticObjectFetcher:
	private val log = LoggerFactory.getLogger(getClass())
	private val http = HttpClient.newHttpClient()
	private val base = serviceBaseUrl.toString.stripSuffix("/")

	def fetchStaticObject(uri: Uri): Validated[StaticObject] =
		get(uri, "staticobject").flatMap(parse[StaticObject])

	def fetchStaticCollection(uri: Uri): Validated[StaticCollection] =
		get(uri, "staticcollection").flatMap(parse[StaticCollection])

	/** Fire-and-forget forward of a dropCache request to the citations service. */
	def dropCache(doi: Doi): Unit =
		// The DOI keeps its slash in the path (the service captures it with `Remaining`), so it must not be encoded.
		val reqUri = URI.create(s"$base/citations/dropCache/$doi")
		val req = HttpRequest.newBuilder(reqUri).POST(HttpRequest.BodyPublishers.noBody()).build()
		http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete: (resp, err) =>
			if err != null then log.error(s"Failed to forward dropCache for $doi to the citations service", err)
			else if resp.statusCode() >= 400 then
				log.error(s"Citations service returned HTTP ${resp.statusCode()} for dropCache of $doi")
		()

	private def get(uri: Uri, path: String): Validated[String] =
		val encoded = URLEncoder.encode(uri.toString, StandardCharsets.UTF_8)
		val reqUri = URI.create(s"$base/citations/$path?uri=$encoded")
		Validated.fromTry:
			Try:
				val req = HttpRequest.newBuilder(reqUri).GET().build()
				val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
				if resp.statusCode() >= 400 then
					throw new RuntimeException(s"Citations service returned HTTP ${resp.statusCode()} for $reqUri: ${resp.body()}")
				resp.body()

	private def parse[T: JsonReader](body: String): Validated[T] =
		Validated.fromTry(Try(body.parseJson.convertTo[T]))

end CitationServiceClient
