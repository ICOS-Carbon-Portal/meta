package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection, RepositoryException}
import org.eclipse.rdf4j.sail.SailConnection
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
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
		StatementPatternErrors.describe(subject.orNull, predicate.orNull, obj.orNull, readContexts):
			repo.access(conn =>
				conn.getStatements(
					subject.getOrElse(null),
					predicate.getOrElse(null),
					obj.getOrElse(null),
					false,
					readContexts*)
			)

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean =
		StatementPatternErrors.describe(subject.orNull, predicate.orNull, obj.orNull, readContexts):
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
		StatementPatternErrors.describe(subject, predicate, obj, readContexts):
			Rdf4jIterationIterator(conn.getStatements(subject, predicate, obj, false, readContexts*))

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		StatementPatternErrors.describe(subject, predicate, obj, readContexts):
			conn.hasStatement(subject, predicate, obj, false, readContexts*)

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		Rdf4jTriplestoreConnection(primary, read, conn)

	override def factory: ValueFactory = conn.getValueFactory

	override def close(): Unit = conn.close()

	override def evaluateGraphQuery(query: String): CloseableIterator[Statement] =
		val rdf4jIter = conn.prepareGraphQuery(QueryLanguage.SPARQL, query).evaluate()
		Rdf4jIterationIterator(rdf4jIter)

	override def evaluateTupleQuery(query: String): CloseableIterator[BindingSet] =
		val rdf4jIter = conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
		Rdf4jIterationIterator(rdf4jIter)

end Rdf4jTriplestoreConnection

object Rdf4jTriplestoreConnection:

	def access[T](repo: Repository)(read: (TriplestoreConnection & SparqlRunner) ?=> T): T =
		val conn = Rdf4jTriplestoreConnection(null, Nil, repo.getConnection())
		try read(using conn) finally conn.close()

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


/** Adds the requested statement pattern to repository errors. Remote (SPARQL-over-HTTP)
  * repositories paste the pattern's values into a query string, so a value that is not
  * representable in SPARQL (e.g. an IRI with a space) yields an opaque parse error.
  */
private object StatementPatternErrors:

	// characters that are not allowed inside a SPARQL IRIREF
	private val invalidIriChars = "[\\x00-\\x20<>\"{}|^`\\\\]".r

	def describe[T](subject: IRI | Null, predicate: IRI | Null, obj: Value | Null, contexts: Seq[IRI])(body: => T): T =
		try body catch case err: RepositoryException =>
			val pattern = Seq(subject, predicate, obj).map(render).mkString(" ")
			val ctxs = if contexts.isEmpty then "all contexts" else contexts.map(render).mkString(", ")
			val badIris = Seq(subject, predicate, obj).collect:
				case iri: IRI if invalidIriChars.findFirstIn(iri.stringValue).isDefined => render(iri)
			val hint =
				if badIris.isEmpty then ""
				else s" Pattern contains IRI(s) not representable in SPARQL: ${badIris.mkString(", ")}."
			throw RepositoryException(
				s"Failed to look up statement pattern ($pattern) in $ctxs.$hint Cause: ${err.getMessage}",
				err
			)

	private def render(v: Value | Null): String = v match
		case null => "?"
		case iri: IRI => s"<${iri.stringValue}>"
		case other: Value => other.toString

end StatementPatternErrors
