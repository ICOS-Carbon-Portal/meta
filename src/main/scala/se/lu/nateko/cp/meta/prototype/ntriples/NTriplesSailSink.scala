package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}
import org.eclipse.rdf4j.sail.SailException
import org.eclipse.rdf4j.sail.base.SailSink
import scala.collection.mutable

class NTriplesSailSink(store: NTriplesSailStore, explicit: Boolean) extends SailSink {

	private val added = mutable.Set[Statement]()
	private val removed = mutable.Set[Statement]()
	private val namespaceChanges = mutable.Map[String, Option[String]]()
	private val contextsToClean = mutable.Set[Resource]()

	override def approve(subj: Resource, pred: IRI, obj: Value, context: Resource): Unit = {
		val st = store.getValueFactory.createStatement(subj, pred, obj, context)
		added += st
		removed -= st
	}

	override def deprecate(st: Statement): Unit = {
		removed += st
		added -= st
	}

	override def clear(contexts: Resource*): Unit = {
		if (contexts.isEmpty) {
			// Clear all contexts - this is a special case
			contextsToClean.clear()
			contextsToClean += null // Signal to clear all
		} else {
			contextsToClean ++= contexts
		}
	}

	override def setNamespace(prefix: String, name: String): Unit = {
		namespaceChanges(prefix) = Some(name)
	}

	override def removeNamespace(prefix: String): Unit = {
		namespaceChanges(prefix) = None
	}

	override def clearNamespaces(): Unit = {
		// Get all current namespaces and mark them for removal
		val iter = store.getNamespaces()
		try {
			while (iter.hasNext) {
				val ns = iter.next()
				namespaceChanges(ns.getPrefix) = None
			}
		} finally {
			iter.close()
		}
	}

	override def prepare(): Unit = {
		// Validation can be done here if needed
		// For this simple implementation, we don't need to do anything
	}

	override def flush(): Unit = {
		try {
			// Clear contexts first
			for (context <- contextsToClean) {
				if (context == null) {
					// Clear all - remove all statements
					val iter = store.getAllStatements()
					try {
						while (iter.hasNext) {
							store.removeStatement(iter.next(), explicit)
						}
					} finally {
						iter.close()
					}
				} else {
					store.clearContext(context)
				}
			}

			// Apply removals
			for (st <- removed) {
				store.removeStatement(st, explicit)
			}

			// Apply additions
			for (st <- added) {
				store.addStatement(st, explicit)
			}

			// Apply namespace changes
			for ((prefix, nameOpt) <- namespaceChanges) {
				nameOpt match {
					case Some(name) => store.setNamespace(prefix, name)
					case None => store.removeNamespace(prefix)
				}
			}

			// Persist to disk after successful transaction
			store.saveToFile()

			// Clear buffers
			clearBuffers()
		} catch {
			case e: Exception =>
				throw new SailException("Failed to flush changes", e)
		}
	}

	override def close(): Unit = {
		// Discard buffered changes on rollback
		clearBuffers()
	}

	private def clearBuffers(): Unit = {
		added.clear()
		removed.clear()
		namespaceChanges.clear()
		contextsToClean.clear()
	}

	// Observe method for monitoring changes (optional)
	override def observe(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = {
		// No-op for simple implementation
	}
}
