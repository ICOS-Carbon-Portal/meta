package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Namespace, Resource, Statement, Value}
import org.eclipse.rdf4j.sail.base.SailDataset

class NTriplesSailDataset(store: NTriplesSailStore, explicit: Boolean) extends SailDataset {

	override def getStatements(
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Resource*
	): CloseableIteration[Statement] = {
		store.getStatements(subj, pred, obj, explicit, contexts.toArray)
	}

	override def getContextIDs(): CloseableIteration[Resource] = {
		store.getContextIDs()
	}

	override def getNamespaces(): CloseableIteration[Namespace] = {
		store.getNamespaces()
	}

	override def getNamespace(prefix: String): String = {
		store.getNamespace(prefix)
	}

	override def close(): Unit = {
		// No resources to clean up
	}
}
