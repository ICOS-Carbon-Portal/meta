package se.lu.nateko.cp.meta.instanceserver

import scala.annotation.migration

import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.RDF

import se.lu.nateko.cp.meta.api.CloseableIterator

trait InstanceServer {

	/**
	 * Makes a new URI for the new instance, but does not add any triples to the repository.
	 * @param prefix The prefix to start the new URI with
	 */
	def makeNewInstance(prefix: URI): URI
	def factory: ValueFactory

	def getStatements(subject: Option[URI], predicate: Option[URI], obj: Option[URI]): CloseableIterator[Statement]
	def addAll(statements: Seq[Statement]): Unit
	def removeAll(statements: Seq[Statement]): Unit
	def shutDown(): Unit


	def getInstances(classUri: URI): Seq[URI] =
		getStatements(None, Some(RDF.TYPE), Some(classUri))
			.map(_.getSubject)
			.collect{case uri: URI => uri}
			.toIndexedSeq

	def getStatements(instUri: URI): Seq[Statement] = getStatements(Some(instUri), None, None).toIndexedSeq

	def getValues(instUri: URI, propUri: URI): Seq[Value] =
		getStatements(Some(instUri), Some(propUri), None)
			.map(_.getObject)
			.toIndexedSeq


	def add(statements: Statement*): Unit = addAll(statements)
	def remove(statements: Statement*): Unit = removeAll(statements)

	def addInstance(instUri: URI, classUri: URI): Unit =
		add(factory.createStatement(instUri, RDF.TYPE, classUri))

	def removeInstance(instUri: URI): Unit = removeAll(getStatements(instUri))

	def addPropertyValue(instUri: URI, propUri: URI, value: Value): Unit =
		add(factory.createStatement(instUri, propUri, value))

	def removePropertyValue(instUri: URI, propUri: URI, value: Value): Unit =
		remove(factory.createStatement(instUri, propUri, value))
}