package se.lu.nateko.cp.meta.instanceserver

import java.util.UUID

import scala.collection.JavaConverters.asScalaBufferConverter

import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.repository.Repository

import info.aduna.iteration.Iterations

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.SesameUtils._

class SesameInstanceServer(repo: Repository, writeContext: URI) extends InstanceServer{

	private[this] val factory = repo.getValueFactory

	def makeNewInstance(prefix: URI): URI =
		factory.createURI(prefix.stringValue.stripSuffix("/") + "/", UUID.randomUUID.toString)

	def getStatements(subject: Option[URI], predicate: Option[URI], obj: Option[URI]): CloseableIterator[Statement] =
		repo.access(conn => 
			conn.getStatements(
				subject.getOrElse(null),
				predicate.getOrElse(null),
				obj.getOrElse(null),
				false,
				writeContext)
		)

	def addAll(statements: Seq[Statement]): Unit = repo.transact(conn => 
		statements.foreach(conn.add(_, writeContext))
	).get

	def removeAll(statements: Seq[Statement]): Unit = repo.transact(conn => 
		statements.foreach(conn.remove(_, writeContext))
	).get

	def shutDown(): Unit = repo.shutDown()

}