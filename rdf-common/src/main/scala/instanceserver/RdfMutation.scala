package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{BNode, IRI, Literal, Resource, Value, ValueFactory}
import spray.json.*

final case class RdfUpdateDto(
	isAssertion: Boolean,
	subjectKind: String,
	subject: String,
	predicate: String,
	objectKind: String,
	obj: String,
	literalAttribute: Option[String]
)

final case class RdfMutation(context: String, updates: Seq[RdfUpdateDto])

object RdfMutation extends DefaultJsonProtocol:
	given RootJsonFormat[RdfUpdateDto] = jsonFormat7(RdfUpdateDto.apply)
	given RootJsonFormat[RdfMutation] = jsonFormat2(RdfMutation.apply)

	def from(context: IRI, updates: Seq[RdfUpdate]): RdfMutation =
		RdfMutation(context.stringValue, updates.map(toDto))

	def toUpdates(mutation: RdfMutation, factory: ValueFactory): (IRI, Seq[RdfUpdate]) =
		factory.createIRI(mutation.context) -> mutation.updates.map(fromDto(_, factory))

	private def toDto(update: RdfUpdate): RdfUpdateDto =
		val statement = update.statement
		val (subjectKind, subject) = resourceParts(statement.getSubject)
		val (objectKind, obj, attribute) = statement.getObject match
			case iri: IRI => ("iri", iri.stringValue, None)
			case bnode: BNode => ("bnode", bnode.getID, None)
			case literal: Literal if literal.getLanguage.isPresent =>
				("language", literal.getLabel, Some(literal.getLanguage.get))
			case literal: Literal =>
				("datatype", literal.getLabel, Some(literal.getDatatype.stringValue))
			case unsupported => throw IllegalArgumentException(s"Unsupported RDF object: $unsupported")
		RdfUpdateDto(
			update.isAssertion,
			subjectKind,
			subject,
			statement.getPredicate.stringValue,
			objectKind,
			obj,
			attribute
		)

	private def fromDto(dto: RdfUpdateDto, factory: ValueFactory): RdfUpdate =
		val subject: Resource = dto.subjectKind match
			case "iri" => factory.createIRI(dto.subject)
			case "bnode" => factory.createBNode(dto.subject)
			case bad => throw IllegalArgumentException(s"Unknown RDF subject kind: $bad")
		val obj: Value = dto.objectKind match
			case "iri" => factory.createIRI(dto.obj)
			case "bnode" => factory.createBNode(dto.obj)
			case "language" => factory.createLiteral(dto.obj, dto.literalAttribute.get)
			case "datatype" => factory.createLiteral(dto.obj, factory.createIRI(dto.literalAttribute.get))
			case bad => throw IllegalArgumentException(s"Unknown RDF object kind: $bad")
		RdfUpdate(
			factory.createStatement(subject, factory.createIRI(dto.predicate), obj),
			dto.isAssertion
		)

	private def resourceParts(resource: Resource): (String, String) = resource match
		case iri: IRI => "iri" -> iri.stringValue
		case bnode: BNode => "bnode" -> bnode.getID
