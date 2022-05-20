package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.UpdateContext
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import scala.util.control.NoStackTrace

class ReadonlyConnectionWrapper(conn: NotifyingSailConnection, errorMessage: String) extends NotifyingSailConnectionWrapper(conn){

	override def addStatement(modify: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		writeFail

	override def addStatement(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = writeFail

	override def clear(contexts: Resource*): Unit = writeFail
	override def clearNamespaces(): Unit = writeFail
	override def removeNamespace(prefix: String) = writeFail

	override def removeStatement(modify: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		writeFail

	override def removeStatements(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = writeFail

	override def startUpdate(modify: UpdateContext): Unit = writeFail
	override def begin(): Unit = writeFail

	private def writeFail: Nothing = throw new IllegalAccessException(errorMessage) with NoStackTrace
}
