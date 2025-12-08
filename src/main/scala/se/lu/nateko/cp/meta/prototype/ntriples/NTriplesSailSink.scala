package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}
import org.eclipse.rdf4j.sail.base.SailSink
import scala.collection.mutable

class NTriplesSailSink(store: NTriplesSailStore, explicit: Boolean) extends SailSink {


	// All write operations are no-ops for read-only store
	override def approve(subj: Resource, pred: IRI, obj: Value, context: Resource): Unit = {
		// No-op - read-only store
	}

	override def deprecate(st: Statement): Unit = {
		// No-op - read-only store
	}

	override def clear(contexts: Resource*): Unit = {
		// No-op - read-only store
	}

	override def setNamespace(prefix: String, name: String): Unit = {
		// No-op - read-only store
	}

	override def removeNamespace(prefix: String): Unit = {
		// No-op - read-only store
	}

	override def clearNamespaces(): Unit = {
		// No-op - read-only store
	}

	override def prepare(): Unit = {
		// Validation can be done here if needed
		// For this simple implementation, we don't need to do anything
	}

	override def flush(): Unit = {
		// No-op - read-only store, just clear empty buffers
		clearBuffers()
	}

	override def close(): Unit = {
		// Discard buffered changes on rollback
		clearBuffers()
	}

	private def clearBuffers(): Unit = {
	}

	// Observe method for monitoring changes (optional)
	override def observe(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = {
		// No-op for simple implementation
	}
}
