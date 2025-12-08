package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.transaction.IsolationLevel
import org.eclipse.rdf4j.sail.base.{BackingSailSource, SailDataset, SailSink}
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Namespace, Resource, Statement, Value}
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import scala.jdk.CollectionConverters.IteratorHasAsJava

class NTriplesSailSource(store: NTriplesSailStore, explicit: Boolean) extends BackingSailSource {
	override def dataset(level: IsolationLevel): SailDataset = {
		new NTriplesSailDataset(store, explicit)
	}

	override def sink(level: IsolationLevel): SailSink = {
		new NoopSink()
	}
}

class NTriplesSailDataset(store: NTriplesSailStore, explicit: Boolean) extends SailDataset {

	override def getStatements(
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Resource*
	): CloseableIteration[Statement] = {
		return new CloseableIteratorIteration(Set().iterator.asJava)
	}

	override def getContextIDs(): CloseableIteration[Resource] = {
		return new CloseableIteratorIteration(Set().iterator.asJava)
	}

	override def getNamespaces(): CloseableIteration[Namespace] = {
		return new CloseableIteratorIteration(Set().iterator.asJava)
	}

	override def getNamespace(prefix: String): String = {
		""
	}

	override def close(): Unit = {
		// No resources to clean up
	}
}

class NoopSink() extends SailSink {
	override def approve(subj: Resource, pred: IRI, obj: Value, context: Resource): Unit = {}
	override def deprecate(st: Statement): Unit = {}
	override def clear(contexts: Resource*): Unit = {}
	override def setNamespace(prefix: String, name: String): Unit = {}
	override def removeNamespace(prefix: String): Unit = {}
	override def clearNamespaces(): Unit = {}
	override def prepare(): Unit = {}
	override def flush(): Unit = {}
	override def close(): Unit = {}
	override def observe(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = {}
}
