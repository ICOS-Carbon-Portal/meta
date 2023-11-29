package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.util.UUID
import scala.util.Try

class Rdf4jInstanceServer(repo: Repository, val readContexts: Seq[IRI], val writeContexts: Seq[IRI]) extends InstanceServer:

	def this(repo: Repository) = this(repo, Nil, Nil)
	def this(repo: Repository, context: IRI) = this(repo, Seq(context), Seq(context))
	def this(repo: Repository, readContext: IRI, writeContext: IRI) = this(repo, Seq(readContext), Seq(writeContext))
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
			if(update.isAssertion) conn.add(update.statement, writeContexts*)
			else conn.remove(update.statement, writeContexts*)
		})
		cotransact
	)

	def filterNotContainedStatements(statements: IterableOnce[Statement]): Seq[Statement] = {
		repo.accessEagerly{ conn =>
			statements.iterator.filter(st => !conn.hasStatement(st, false, readContexts*)).toIndexedSeq
		}
	}

	def withContexts(read: Seq[IRI], write: Seq[IRI]) = new Rdf4jInstanceServer(repo, read, write)

	def getConnection(): TriplestoreConnection = Rdf4jTriplestoreConnection(writeContexts.head, readContexts, repo.getConnection())

	override def shutDown(): Unit = repo.shutDown()

end Rdf4jInstanceServer


class Rdf4jTriplestoreConnection(val primaryContext: IRI, val readContexts: Seq[IRI], conn: RepositoryConnection) extends TriplestoreConnection:

	override def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean =
		conn.hasStatement(subject.getOrElse(null), predicate.getOrElse(null), obj.getOrElse(null), false, readContexts*)

	override def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] =
		Rdf4jIterationIterator(
			conn.getStatements(subject.getOrElse(null), predicate.getOrElse(null), obj.getOrElse(null), false, readContexts*)
		)

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
