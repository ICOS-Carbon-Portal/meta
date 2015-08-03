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

class SesameInstanceServer(repo: Repository, writeContext: URI) extends InstanceServer{

	def this(repo: Repository, writeContext: String) = this(repo, repo.getValueFactory.createURI(writeContext))

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
				writeContext)
		)

	def applyAll(updates: Seq[RdfUpdate]): Try[Unit] = repo.transact(conn => 
		updates.foreach(update => {
			if(update.isAssertion) conn.add(update.statement, writeContext)
			else conn.remove(update.statement, writeContext)
		})
	)

	def shutDown(): Unit = repo.shutDown()

}