package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.{IRI, Statement, Value}
import scala.util.Try
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}

class WriteNotifyingInstanceServer(val inner: InstanceServer) extends InstanceServer:

	private var cb: Function0[Unit] = () => ()

	def setSubscriber(sub: () => Unit): Unit = cb = sub
	def unsetSubscriber(): Unit = cb = () => ()

	def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit] =
		inner.applyAll(updates){
			if(!updates.isEmpty) cb()
			cotransact
		}


	def factory = inner.factory
	def getStatements(subj: Option[IRI], pred: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] = inner.getStatements(subj, pred, obj)
	def makeNewInstance(prefix: IRI): IRI = inner.makeNewInstance(prefix)
	def readContexts = inner.readContexts
	def writeContext = inner.writeContext
	def withContexts(read: Seq[IRI], write: IRI): InstanceServer = inner.withContexts(read, write)
	override def getConnection(): TriplestoreConnection & SparqlRunner = inner.getConnection()

	override def shutDown(): Unit = inner.shutDown()

end WriteNotifyingInstanceServer
