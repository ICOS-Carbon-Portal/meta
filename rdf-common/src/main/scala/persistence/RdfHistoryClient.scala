package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import spray.json.*

import java.net.URI
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

final class RdfHistoryClient(endpoint: URI, factory: ValueFactory)(using system: ActorSystem):
	private given ExecutionContext = system.dispatcher

	def history(contexts: Seq[IRI]): Future[Seq[(Instant, RdfUpdate)]] =
		val params = contexts.map(context => "context" -> context.stringValue)
		val uri = Uri(endpoint.toString).withQuery(Uri.Query(params*))
		Http().singleRequest(akka.http.scaladsl.model.HttpRequest(uri = uri)).flatMap: response =>
			response.entity.toStrict(30.seconds).flatMap: entity =>
				if response.status == StatusCodes.OK then
					Future.successful(entity.data.utf8String.parseJson.convertTo[Seq[RdfHistoryEntry]].map(
						RdfHistoryEntry.toUpdate(_, factory)
					))
				else Future.failed(RuntimeException(
					s"rdfStore history request failed with ${response.status}: ${entity.data.utf8String}"
				))
