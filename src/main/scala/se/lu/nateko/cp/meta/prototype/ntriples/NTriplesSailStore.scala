package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import java.io.File
import java.util.concurrent.locks.{ReentrantReadWriteLock, Lock => JLock}
import org.eclipse.rdf4j.common.iteration.{CloseableIteration, CloseableIteratorIteration, EmptyIteration}
import org.eclipse.rdf4j.model.{IRI, Namespace, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.model.impl.{SimpleNamespace, SimpleValueFactory}
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import org.eclipse.rdf4j.sail.base.{SailSource, SailStore}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class NTriplesSailStore(dataFile: File) extends SailStore {

	private val valueFactory = SimpleValueFactory.getInstance()
	private val statements = mutable.ArrayBuffer[Statement]()
	private val namespaces = mutable.Map[String, String]()

	// Lock for coordinating access
	private val lock = new ReentrantReadWriteLock()

	// Indexes for efficient queries - map each value to list of statements
	private val subjectIndex = mutable.Map[Resource, mutable.ArrayBuffer[Statement]]()
	private val predicateIndex = mutable.Map[IRI, mutable.ArrayBuffer[Statement]]()
	private val objectIndex = mutable.Map[Value, mutable.ArrayBuffer[Statement]]()
	private val contextIndex = mutable.Map[Resource, mutable.ArrayBuffer[Statement]]()

	private val explicitSource = new NTriplesSailSource(this, explicit = true)
	private val inferredSource = new NTriplesSailSource(this, explicit = false)

	override def getValueFactory: ValueFactory = valueFactory

	override def getExplicitSailSource: SailSource = explicitSource

	override def getInferredSailSource: SailSource = inferredSource

	override def getEvaluationStatistics: EvaluationStatistics =
		new EvaluationStatistics()

	override def close(): Unit = {
		// No resources to clean up in this simple implementation
	}

	def getReadLock: JLock = lock.readLock()
	def getWriteLock: JLock = lock.writeLock()

	// Core query method
	def getStatements(
		subj: Resource,
		pred: IRI,
		obj: Value,
		explicit: Boolean,
		contexts: Array[Resource]
	): CloseableIteration[Statement] = {

		lock.readLock().lock()
		try {
			// For this simple implementation, we only handle explicit statements
			if (!explicit) {
				return new EmptyIteration[Statement]()
			}

			// Choose the smallest index to iterate
			val candidates = chooseIndex(subj, pred, obj, contexts)

			// Filter candidates
			val matched = candidates.filter { st =>
				matchesPattern(st, subj, pred, obj, contexts)
			}

			new CloseableIteratorIteration(matched.iterator.asJava)
		} finally {
			lock.readLock().unlock()
		}
	}

	private def chooseIndex(
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Array[Resource]
	): Seq[Statement] = {

		// Select smallest index
		if (subj != null) {
			subjectIndex.getOrElse(subj, mutable.ArrayBuffer.empty).toSeq
		} else if (pred != null) {
			predicateIndex.getOrElse(pred, mutable.ArrayBuffer.empty).toSeq
		} else if (obj != null) {
			objectIndex.getOrElse(obj, mutable.ArrayBuffer.empty).toSeq
		} else if (contexts != null && contexts.length == 1) {
			val ctx = contexts(0)
			contextIndex.getOrElse(ctx, mutable.ArrayBuffer.empty).toSeq
		} else {
			// Full scan
			statements.toSeq
		}
	}

	private def matchesPattern(
		st: Statement,
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Array[Resource]
	): Boolean = {

		// Check subject
		if (subj != null && !st.getSubject.equals(subj)) return false

		// Check predicate
		if (pred != null && !st.getPredicate.equals(pred)) return false

		// Check object
		if (obj != null && !st.getObject.equals(obj)) return false

		// Check context
		if (contexts != null && contexts.length > 0) {
			val stCtx = st.getContext
			var found = false
			var i = 0
			while (i < contexts.length && !found) {
				val ctx = contexts(i)
				if ((ctx == null && stCtx == null) || (ctx != null && ctx.equals(stCtx))) {
					found = true
				}
				i += 1
			}
			if (!found) return false
		}

		true
	}

	// Core write methods (no-ops for read-only store)
	def addStatement(st: Statement, explicit: Boolean): Unit = {
		// No-op - read-only store
	}

	def removeStatement(st: Statement, explicit: Boolean): Unit = {
		// No-op - read-only store
	}

	private def containsStatement(st: Statement): Boolean = {
		statements.exists(statementsEqual(_, st))
	}

	private def matchesStatement(existing: Statement, st: Statement): Boolean = {
		existing.getSubject.equals(st.getSubject) &&
		existing.getPredicate.equals(st.getPredicate) &&
		existing.getObject.equals(st.getObject) &&
		(
			(existing.getContext == null && st.getContext == null) ||
			(existing.getContext != null && existing.getContext.equals(st.getContext))
		)
	}

	private def statementsEqual(st1: Statement, st2: Statement): Boolean = {
		st1.getSubject.equals(st2.getSubject) &&
		st1.getPredicate.equals(st2.getPredicate) &&
		st1.getObject.equals(st2.getObject) &&
		((st1.getContext == null && st2.getContext == null) ||
		 (st1.getContext != null && st1.getContext.equals(st2.getContext)))
	}

	private def removeFromIndexes(st: Statement): Unit = {
		subjectIndex.get(st.getSubject).foreach(_ -= st)
		predicateIndex.get(st.getPredicate).foreach(_ -= st)
		objectIndex.get(st.getObject).foreach(_ -= st)
		val ctx = st.getContext
		contextIndex.get(ctx).foreach(_ -= st)
	}

	def clearContext(context: Resource): Unit = {
		// No-op - read-only store
	}

	// Namespace operations (no-ops for read-only store)
	def setNamespace(prefix: String, name: String): Unit = {
		// No-op - read-only store
	}

	def removeNamespace(prefix: String): Unit = {
		// No-op - read-only store
	}

	def getNamespace(prefix: String): String = {
		lock.readLock().lock()
		try {
			namespaces.getOrElse(prefix, null)
		} finally {
			lock.readLock().unlock()
		}
	}

	def getNamespaces(): CloseableIteration[Namespace] = {
		lock.readLock().lock()
		try {
			val nsList = namespaces.map { case (prefix, name) =>
				new SimpleNamespace(prefix, name)
			}.toSeq
			new CloseableIteratorIteration(nsList.iterator.asJava)
		} finally {
			lock.readLock().unlock()
		}
	}

	def getContextIDs(): CloseableIteration[Resource] = {
		lock.readLock().lock()
		try {
			val contexts = contextIndex.keys.filterNot(_ == null).toSeq
			new CloseableIteratorIteration(contexts.iterator.asJava)
		} finally {
			lock.readLock().unlock()
		}
	}

	// For file I/O
	def getAllStatements(): CloseableIteration[Statement] = {
		lock.readLock().lock()
		try {
			val stList = statements.toSeq
			new CloseableIteratorIteration(stList.iterator.asJava)
		} finally {
			lock.readLock().unlock()
		}
	}

	// Persistence operations
	def loadFromFile(): Unit = {
		NTriplesFileIO.load(dataFile, this)
	}

	def saveToFile(): Unit = {
		// No-op - read-only store
	}
}
