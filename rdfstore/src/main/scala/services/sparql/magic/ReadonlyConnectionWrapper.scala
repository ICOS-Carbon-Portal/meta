package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.eclipse.rdf4j.sail.{NotifyingSailConnection, UpdateContext}

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

	private def writeFail: Nothing = throw WritingForbiddenException(errorMessage)


}

class WritingForbiddenException(msg: String) extends IllegalAccessException(msg) with NoStackTrace