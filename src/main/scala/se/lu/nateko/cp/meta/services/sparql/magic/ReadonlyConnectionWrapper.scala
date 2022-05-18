package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.UpdateContext
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value

class ReadonlyConnectionWrapper(conn: NotifyingSailConnection, errorMessage: String) extends NotifyingSailConnectionWrapper(conn){

	override def addStatement(modify: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		throw new IllegalAccessException(errorMessage)

	override def addStatement(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		throw new IllegalAccessException(errorMessage)

	override def clear(contexts: Resource*): Unit = throw new IllegalAccessException(errorMessage)
	override def clearNamespaces(): Unit = throw new IllegalAccessException(errorMessage)
	override def removeNamespace(prefix: String) = throw new IllegalAccessException(errorMessage)

	override def removeStatement(modify: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		throw new IllegalAccessException(errorMessage)

	override def removeStatements(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		throw new IllegalAccessException(errorMessage)

	override def startUpdate(modify: UpdateContext): Unit = throw new IllegalAccessException(errorMessage)
}
