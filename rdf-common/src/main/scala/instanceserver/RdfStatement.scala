package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}

/** An RDF statement as exposed by StatementSource: an SPO triple with no graph context. */
final case class RdfStatement(subject: Resource, predicate: IRI, obj: Value):
	def getSubject: Resource = subject
	def getPredicate: IRI = predicate
	def getObject: Value = obj

	def toRdf4jStatement(using factory: ValueFactory): Statement =
		factory.createStatement(subject, predicate, obj)

object RdfStatement:
	def fromRdf4jStatement(statement: Statement): RdfStatement =
		RdfStatement(statement.getSubject, statement.getPredicate, statement.getObject)
