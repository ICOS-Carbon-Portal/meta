package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import spray.json.*

import java.net.URI
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

final case class RdfHistoryEntry(
	timestamp: String,
	isAssertion: Boolean,
	subject: String,
	predicate: String,
	objectKind: String,
	objectValue: String,
	literalAttribute: Option[String]
)

object RdfHistoryEntry extends DefaultJsonProtocol:
	given RootJsonFormat[RdfHistoryEntry] = jsonFormat7(RdfHistoryEntry.apply)

	def from(timestamp: Instant, update: RdfUpdate): RdfHistoryEntry =
		val statement = update.statement
		val (kind, value, attribute) = statement.getObject match
			case iri: IRI => ("iri", iri.stringValue, None)
			case literal: Literal if literal.getLanguage.isPresent =>
				("language", literal.getLabel, Some(literal.getLanguage.get))
			case literal: Literal => ("datatype", literal.getLabel, Some(literal.getDatatype.stringValue))
			case unsupported => throw IllegalArgumentException(s"Unsupported RDF-log object: $unsupported")
		RdfHistoryEntry(
			timestamp.toString,
			update.isAssertion,
			statement.getSubject.stringValue,
			statement.getPredicate.stringValue,
			kind,
			value,
			attribute
		)

	def toUpdate(entry: RdfHistoryEntry, factory: ValueFactory): (Instant, RdfUpdate) =
		val subject = factory.createIRI(entry.subject)
		val predicate = factory.createIRI(entry.predicate)
		val obj = entry.objectKind match
			case "iri" => factory.createIRI(entry.objectValue)
			case "language" => factory.createLiteral(entry.objectValue, entry.literalAttribute.get)
			case "datatype" => factory.createLiteral(entry.objectValue, factory.createIRI(entry.literalAttribute.get))
			case bad => throw IllegalArgumentException(s"Unknown RDF-history object kind: $bad")
		Instant.parse(entry.timestamp) -> RdfUpdate(
			factory.createStatement(subject, predicate, obj),
			entry.isAssertion
		)

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
