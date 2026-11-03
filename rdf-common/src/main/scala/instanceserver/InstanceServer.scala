package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.services.upload.MetadataUpdater
import scala.util.Try

trait InstanceServer extends AutoCloseable:

	/**
	 * Makes a new IRI for the new instance, but does not add any triples to the repository.
	 * @param prefix The prefix to start the new IRI with
	 */
	def makeNewInstance(prefix: IRI): IRI
	def factory: ValueFactory
	def readContexts: Seq[IRI]
	def writeContext: IRI
	def withContexts(read: Seq[IRI], write: IRI): InstanceServer
	def getConnection(): TriplestoreConnection & SparqlRunner

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement]
	def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit]
	def shutDown(): Unit

	final def access[T](read: (TriplestoreConnection & SparqlRunner) ?=> T): T =
		val conn = getConnection()
		try read(using conn) finally conn.close()

	final def filterNotContainedStatements(statements: IterableOnce[Statement]): IndexedSeq[Statement] = access: conn ?=>
		statements.iterator.filterNot(conn.hasStatement).toIndexedSeq

	final def writeContextsView: InstanceServer = withContexts(Seq(writeContext), writeContext)

	final def addAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, true)))()
	final def removeAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, false)))()

	final override def close(): Unit = shutDown()

	final def add(statements: Statement*): Try[Unit] = addAll(statements)
	final def remove(statements: Statement*): Try[Unit] = removeAll(statements)

	final def addInstance(instUri: IRI, classUri: IRI): Try[Unit] =
		add(factory.createStatement(instUri, RDF.TYPE, classUri))

	final def addPropertyValue(instUri: IRI, propUri: IRI, value: Value): Try[Unit] =
		add(factory.createStatement(instUri, propUri, value))

	final def removePropertyValue(instUri: IRI, propUri: IRI, value: Value): Try[Unit] =
		remove(factory.createStatement(instUri, propUri, value))

	final def applyDiff(from: Seq[Statement], to: Seq[Statement]): Unit =
		val updates = MetadataUpdater.diff(from, to, factory)
		applyAll(updates)()

end InstanceServer


trait TriplestoreConnection extends AutoCloseable, StatementSource {
	def primaryContext: IRI
	def readContexts: Seq[IRI]
	def factory: ValueFactory
	def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection

	final def withReadContexts(read: Seq[IRI]): TriplestoreConnection =
		if readContexts == read then this
		else withContexts(primaryContext, read)

	final def primaryContextView: TriplestoreConnection =
		if readContexts.length == 1 && readContexts.head == primaryContext then this
		else withContexts(primaryContext, Seq(primaryContext))
}
