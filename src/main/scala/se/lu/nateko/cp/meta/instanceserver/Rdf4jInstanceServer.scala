package se.lu.nateko.cp.meta.instanceserver

import java.util.UUID
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.rdf4j.*
import scala.util.Try
import org.eclipse.rdf4j.model.Value

class Rdf4jInstanceServer(repo: Repository, val readContexts: Seq[IRI], val writeContexts: Seq[IRI]) extends InstanceServer{

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

	def applyAll(updates: Seq[RdfUpdate]): Try[Unit] = repo.transact(conn => 
		updates.foreach(update => {
			if(update.isAssertion) conn.add(update.statement, writeContexts*)
			else conn.remove(update.statement, writeContexts*)
		})
	)

	def filterNotContainedStatements(statements: IterableOnce[Statement]): Seq[Statement] = {
		repo.accessEagerly{ conn =>
			statements.iterator.filter(st => !conn.hasStatement(st, false, readContexts*)).toIndexedSeq
		}
	}

	def withContexts(read: Seq[IRI], write: Seq[IRI]) = new Rdf4jInstanceServer(repo, read, write)

}