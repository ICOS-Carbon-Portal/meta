package se.lu.nateko.cp.meta.instanceserver

import java.util.UUID
import scala.collection.JavaConverters.asScalaBufferConverter
import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.repository.Repository
import info.aduna.iteration.Iterations
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.sesame._
import scala.util.Try
import org.openrdf.model.Value

class SesameInstanceServer(repo: Repository, val readContexts: Seq[URI], val writeContexts: Seq[URI]) extends InstanceServer{

	def this(repo: Repository) = this(repo, Nil, Nil)
	def this(repo: Repository, context: URI) = this(repo, Seq(context), Seq(context))
	def this(repo: Repository, readContext: URI, writeContext: URI) = this(repo, Seq(readContext), Seq(writeContext))
	def this(repo: Repository, contextUri: String) = this(repo, repo.getValueFactory.createURI(contextUri))

	val factory = repo.getValueFactory

	def makeNewInstance(prefix: URI): URI =
		factory.createURI(prefix.stringValue.stripSuffix("/") + "/", UUID.randomUUID.toString)

	def getStatements(subject: Option[URI], predicate: Option[URI], obj: Option[Value]): CloseableIterator[Statement] =
		repo.access(conn => 
			conn.getStatements(
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

	def shutDown(): Unit = repo.shutDown()

}