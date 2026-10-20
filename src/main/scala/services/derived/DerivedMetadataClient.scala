package se.lu.nateko.cp.meta.services.derived

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest}
import akka.stream.Materializer
import se.lu.nateko.cp.meta.core.data.{DataObject, DocObject, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.citation.RemoteCitation
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataJsonProtocol.given
import se.lu.nateko.cp.meta.utils.Validated
import spray.json.*

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

/** An item enriched with derived metadata, and the status of its citation from a DOI or an external repository */
case class WithRemoteCitation[+T](item: T, remoteCitation: RemoteCitation)

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

	def enrich(resource: URI, item: StaticObject): Future[Validated[StaticObject]] =
		enrichWithRemoteCitation(resource, item).map(_.map(_.item))

	def enrich(resource: URI, item: StaticCollection): Future[Validated[StaticCollection]] =
		enrichWithRemoteCitation(resource, item).map(_.map(_.item))

	def enrichWithRemoteCitation(resource: URI, item: StaticObject): Future[Validated[WithRemoteCitation[StaticObject]]] =
		enrichWith(resource, item): metadata =>
			item match
				case data: DataObject => data.copy(references = metadata.references)
				case doc: DocObject => doc.copy(references = metadata.references)

	def enrichWithRemoteCitation(resource: URI, item: StaticCollection): Future[Validated[WithRemoteCitation[StaticCollection]]] =
		enrichWith(resource, item)(metadata => item.copy(references = metadata.references))

	/** The errors are those of deriving the metadata, to be reported after the errors of reading the item itself */
	private def enrichWith[T](resource: URI, item: T)(enrich: DerivedMetadata => T): Future[Validated[WithRemoteCitation[T]]] =
		resolve(resource).map:
			case DerivedMetadataResult(_, "ready", Some(metadata), _) =>
				val enriched = WithRemoteCitation(enrich(metadata), metadata.remoteCitation.getOrElse(RemoteCitation.NotApplicable))
				new Validated(Some(enriched), metadata.warnings)
			case DerivedMetadataResult(_, "failed", _, errors) => new Validated(None, errors)
			case _ => Validated.ok(WithRemoteCitation(item, RemoteCitation.NotApplicable))

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
