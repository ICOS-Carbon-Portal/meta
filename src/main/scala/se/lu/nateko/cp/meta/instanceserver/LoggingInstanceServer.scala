package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import scala.util.Try
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.persistence.RdfUpdateLog

class LoggingInstanceServer(inner: InstanceServer, val log: RdfUpdateLog) extends InstanceServer:

	def factory = inner.factory
	def readContexts = inner.readContexts
	def writeContext = inner.writeContext
	def makeNewInstance(prefix: IRI): IRI = inner.makeNewInstance(prefix)

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] =
		inner.getStatements(subject, predicate, obj)

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

	override def getConnection(): TriplestoreConnection & SparqlRunner = inner.getConnection()

end LoggingInstanceServer
