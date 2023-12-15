package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import se.lu.nateko.cp.meta.persistence.RdfUpdateLog

import scala.util.Try

class LoggingInstanceServer(inner: InstanceServer, val log: RdfUpdateLog) extends InstanceServer:

	def factory = inner.factory
	def readContexts = inner.readContexts
	def writeContext = inner.writeContext
	def makeNewInstance(prefix: IRI) = inner.makeNewInstance(prefix)

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]) =
		inner.getStatements(subject, predicate, obj)

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean =
		inner.hasStatement(subject, predicate, obj)

	def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit] =
		inner.applyAll(updates){
			log.appendAll(updates)
			cotransact
		}

	override def shutDown(): Unit = {
		inner.shutDown()
		log.close()
	}

	def withContexts(read: Seq[IRI], write: IRI) = new LoggingInstanceServer(inner.withContexts(read, write), log)

	def getConnection(): TriplestoreConnection = inner.getConnection()

end LoggingInstanceServer
