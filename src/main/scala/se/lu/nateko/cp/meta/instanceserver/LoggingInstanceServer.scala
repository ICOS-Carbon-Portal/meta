package se.lu.nateko.cp.meta.instanceserver

import org.openrdf.model.Statement
import org.openrdf.model.URI

import se.lu.nateko.cp.meta.persistence.RdfUpdate
import se.lu.nateko.cp.meta.persistence.RdfUpdateLog

class LoggingInstanceServer(inner: InstanceServer, log: RdfUpdateLog) extends InstanceServer{

	def factory = inner.factory
	def makeNewInstance(prefix: URI) = inner.makeNewInstance(prefix)

	def getStatements(subject: Option[URI], predicate: Option[URI], obj: Option[URI]) =
		inner.getStatements(subject, predicate, obj)

	def addAll(statements: Seq[Statement]): Unit = {
		val assertions = statements.map(RdfUpdate(_, true))
		log.appendAll(assertions)
		inner.addAll(statements)
	}

	def removeAll(statements: Seq[Statement]): Unit = {
		val retractions = statements.map(RdfUpdate(_, false))
		log.appendAll(retractions)
		inner.removeAll(statements)
	}

	def shutDown(): Unit = {
		inner.shutDown()
		log.close()
	}
}