package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value

import scala.util.Try

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
	def filterNotContainedStatements(stats: IterableOnce[Statement]) = inner.filterNotContainedStatements(stats)
	def getStatements(subj: Option[IRI], pred: Option[IRI], obj: Option[Value]) = inner.getStatements(subj, pred, obj)
	def hasStatement(subj: Option[IRI], pred: Option[IRI], obj: Option[Value]) = inner.hasStatement(subj, pred, obj)
	def makeNewInstance(prefix: IRI) = inner.makeNewInstance(prefix)
	def readContexts = inner.readContexts
	def writeContexts = inner.writeContexts
	def withContexts(read: Seq[IRI], write: Seq[IRI]) = inner.withContexts(read, write)
	def getConnection(): TriplestoreConnection = inner.getConnection()

	override def shutDown(): Unit = inner.shutDown()

end WriteNotifyingInstanceServer
