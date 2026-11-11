package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import org.eclipse.rdf4j.sail.SailConnection
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery, SparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.util.UUID
import scala.util.Try

class Rdf4jInstanceServer(repo: Repository, val readContexts: Seq[IRI], val writeContext: IRI) extends InstanceServer:

	def this(repo: Repository) = this(repo, Nil, null)
	def this(repo: Repository, context: IRI) = this(repo, Seq(context), context)
	def this(repo: Repository, readContext: IRI, writeContext: IRI) = this(repo, Seq(readContext), writeContext)
	def this(repo: Repository, contextUri: String) = this(repo, repo.getValueFactory.createIRI(contextUri))

	val factory = repo.getValueFactory

	def makeNewInstance(prefix: IRI): IRI =
		factory.createIRI(prefix.stringValue.stripSuffix("/") + "/", UUID.randomUUID.toString)

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] =
		repo.access(conn => 
			conn.getStatements(
				subject.getOrElse(null),
				predicate.getOrElse(null),
				obj.getOrElse(null),
				false,
				readContexts*)
		)

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean =
		repo.accessEagerly(conn =>
			conn.hasStatement(
				subject.getOrElse(null),
				predicate.getOrElse(null),
				obj.getOrElse(null),
				false,
				readContexts*)
		)

	def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit] = repo.transact(conn =>
		updates.foreach(update => {
			if(update.isAssertion) conn.add(update.statement, writeContext)
			else conn.remove(update.statement, writeContext)
		})
		cotransact
	)

	override def withContexts(read: Seq[IRI], write: IRI) = new Rdf4jInstanceServer(repo, read, write)

	override def getConnection(): TriplestoreConnection & SparqlRunner =
		Rdf4jTriplestoreConnection(writeContext, readContexts, repo.getConnection())

	override def shutDown(): Unit = repo.shutDown()

end Rdf4jInstanceServer


class Rdf4jTriplestoreConnection(
	val primaryContext: IRI, val readContexts: Seq[IRI], conn: RepositoryConnection
) extends TriplestoreConnection with SparqlRunner:

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		Rdf4jIterationIterator(conn.getStatements(subject, predicate, obj, false, readContexts*))

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		conn.hasStatement(subject, predicate, obj, false, readContexts*)

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		Rdf4jTriplestoreConnection(primary, read, conn)

	override def factory: ValueFactory = conn.getValueFactory

	override def close(): Unit = conn.close()

	override def evaluateGraphQuery(q: SparqlQuery): CloseableIterator[Statement] =
		val rdf4jIter = conn.prepareGraphQuery(QueryLanguage.SPARQL, q.query).evaluate()
		Rdf4jIterationIterator(rdf4jIter)

	override def evaluateTupleQuery(q: SparqlQuery): CloseableIterator[BindingSet] =
		val rdf4jIter = conn.prepareTupleQuery(QueryLanguage.SPARQL, q.query).evaluate()
		Rdf4jIterationIterator(rdf4jIter)

end Rdf4jTriplestoreConnection

class Rdf4jSailConnection(
	val primaryContext: IRI, val readContexts: Seq[IRI], conn: SailConnection, val factory: ValueFactory
) extends TriplestoreConnection:

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		Rdf4jIterationIterator(conn.getStatements(subject, predicate, obj, false, readContexts*))

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		conn.hasStatement(subject, predicate, obj, false, readContexts*)

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		Rdf4jSailConnection(primary, read, conn, factory)

	override def close(): Unit = conn.close()

end Rdf4jSailConnection
