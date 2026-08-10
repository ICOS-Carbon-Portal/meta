package se.lu.nateko.cp.meta.services.derived

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.stream.Materializer
import se.lu.nateko.cp.meta.core.data.{DataObject, DocObject, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataJsonProtocol.given
import spray.json.*

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

/** Private client for rdfStore's derived-metadata boundary. */
final class DerivedMetadataClient(endpoint: URI)(using system: ActorSystem, mat: Materializer):

	private given ExecutionContext = system.dispatcher

	def resolve(resources: Seq[URI]): Future[DerivedMetadataResponse] =
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = endpoint.toString,
			entity = HttpEntity(ContentTypes.`application/json`, DerivedMetadataRequest(resources).toJson.compactPrint)
		)
		Http().singleRequest(request).flatMap: response =>
			response.entity.toStrict(10.seconds).flatMap: entity =>
				if response.status.isSuccess then
					Future.successful(entity.data.utf8String.parseJson.convertTo[DerivedMetadataResponse])
				else Future.failed(RuntimeException(
					s"rdfStore derived metadata request failed with ${response.status}: ${entity.data.utf8String}"
				))

	def resolve(resource: URI): Future[DerivedMetadataResult] =
		resolve(Seq(resource)).map(_.results.headOption.getOrElse(DerivedMetadataResult(resource, "notFound", None)))

	/**
	 * Replaces the reference fields calculated locally while parsing an item with rdfStore's
	 * canonical derived values. A missing result deliberately leaves the parsed item intact:
	 * callers can still render ordinary RDF metadata when a resource is not citable.
	 */
	def enrich(resource: URI, item: StaticObject): Future[StaticObject] =
		resolve(resource).map:
			case DerivedMetadataResult(_, "ready", Some(metadata)) => item match
				case data: DataObject => data.copy(references = metadata.references)
				case doc: DocObject => doc.copy(references = metadata.references)
			case _ => item

	def enrich(resource: URI, item: StaticCollection): Future[StaticCollection] =
		resolve(resource).map:
			case DerivedMetadataResult(_, "ready", Some(metadata)) => item.copy(references = metadata.references)
			case _ => item

	def dropDoiCache(doi: String): Future[Unit] =
		val request = HttpRequest(
			method = HttpMethods.POST,
			uri = endpoint.toString.stripSuffix("/resolve") + "/drop-cache/" + doi
		)
		Http().singleRequest(request).flatMap: response =>
			response.entity.discardBytes()
			if response.status.isSuccess then Future.successful(())
			else Future.failed(RuntimeException(s"rdfStore rejected DOI cache invalidation for $doi: ${response.status}"))

object DerivedMetadataClient:
	def apply(endpoint: URI)(using ActorSystem, Materializer): DerivedMetadataClient =
		new DerivedMetadataClient(endpoint)
