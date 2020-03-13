package se.lu.nateko.cp.meta.instanceserver

import scala.util.Try

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value

class WriteNotifyingInstanceServer(val inner: InstanceServer) extends InstanceServer {

	private[this] var cb: Function0[Unit] = () => ()

	def setSubscriber(sub: () => Unit): Unit = cb = sub
	def unsetSubscriber(): Unit = cb = () => ()

	def applyAll(updates: Seq[RdfUpdate]): Try[Unit] = {
		val res = inner.applyAll(updates)
		if(!updates.isEmpty) cb()
		res
	}

	def factory = inner.factory
	def filterNotContainedStatements(stats: IterableOnce[Statement]) = inner.filterNotContainedStatements(stats)
	def getStatements(subj: Option[IRI], pred: Option[IRI], obj: Option[Value]) = inner.getStatements(subj, pred, obj)
	def hasStatement(subj: Option[IRI], pred: Option[IRI], obj: Option[Value]) = inner.hasStatement(subj, pred, obj)
	def makeNewInstance(prefix: IRI) = inner.makeNewInstance(prefix)
	def readContexts = inner.readContexts
	def writeContexts = inner.writeContexts
	def writeContextsView = inner.writeContextsView
}
