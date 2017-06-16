package se.lu.nateko.cp.meta.instanceserver

import java.util.UUID
import scala.collection.JavaConverters.asScalaBufferConverter
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.common.iteration.Iterations
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.sesame._
import scala.util.Try
import org.eclipse.rdf4j.model.Value

class SesameInstanceServer(repo: Repository, val readContexts: Seq[IRI], val writeContexts: Seq[IRI]) extends InstanceServer{

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
				readContexts :_*)
		)

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean =
		repo.accessEagerly(conn =>
			conn.hasStatement(
				subject.getOrElse(null),
				predicate.getOrElse(null),
				obj.getOrElse(null),
				false,
				readContexts :_*)
		)

	def applyAll(updates: Seq[RdfUpdate]): Try[Unit] = repo.transact(conn => 
		updates.foreach(update => {
			if(update.isAssertion) conn.add(update.statement, writeContexts :_*)
			else conn.remove(update.statement, writeContexts :_*)
		})
	)

	def filterNotContainedStatements(statements: TraversableOnce[Statement]): Seq[Statement] = {
		repo.accessEagerly{ conn =>
			statements.filter(st => !conn.hasStatement(st, false, readContexts :_*)).toIndexedSeq
		}
	}

	def writeContextsView = new SesameInstanceServer(repo, writeContexts, writeContexts)

}