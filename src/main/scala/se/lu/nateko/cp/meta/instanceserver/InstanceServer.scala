package se.lu.nateko.cp.meta.instanceserver

import scala.annotation.migration
import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.meta.api.CloseableIterator
import scala.util.Try

trait InstanceServer {

	/**
	 * Makes a new URI for the new instance, but does not add any triples to the repository.
	 * @param prefix The prefix to start the new URI with
	 */
	def makeNewInstance(prefix: URI): URI
	def readContexts: Seq[URI]
	def writeContexts: Seq[URI]
	def factory: ValueFactory

	def getStatements(subject: Option[URI], predicate: Option[URI], obj: Option[Value]): CloseableIterator[Statement]
	def filterNotContainedStatements(statements: TraversableOnce[Statement]): Seq[Statement]
	def applyAll(updates: Seq[RdfUpdate]): Try[Unit]
	def shutDown(): Unit = {}

	def addAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, true)))
	def removeAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, false)))

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

	def hasStatement(subject: URI, predicate: URI, obj: Value): Boolean =
		getStatements(Some(subject), Some(predicate), Some(obj)).toIndexedSeq.nonEmpty

	def add(statements: Statement*): Try[Unit] = addAll(statements)
	def remove(statements: Statement*): Try[Unit] = removeAll(statements)

	def addInstance(instUri: URI, classUri: URI): Try[Unit] =
		add(factory.createStatement(instUri, RDF.TYPE, classUri))

	def removeInstance(instUri: URI): Unit = removeAll(getStatements(instUri))

	def addPropertyValue(instUri: URI, propUri: URI, value: Value): Try[Unit] =
		add(factory.createStatement(instUri, propUri, value))

	def removePropertyValue(instUri: URI, propUri: URI, value: Value): Try[Unit] =
		remove(factory.createStatement(instUri, propUri, value))

	def applyDiff(from: Seq[Statement], to: Seq[Statement]): Unit = {
		val toRemove = from.diff(to)
		val toAdd = to.diff(from)

		applyAll(toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true)))
	}
}