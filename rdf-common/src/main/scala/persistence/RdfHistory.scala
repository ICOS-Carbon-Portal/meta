package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import spray.json.*

import java.time.Instant

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
