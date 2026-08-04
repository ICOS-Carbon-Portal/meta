package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.common.transaction.IsolationLevel
import org.eclipse.rdf4j.sail.helpers.{NotifyingSailConnectionWrapper, NotifyingSailWrapper}
import org.eclipse.rdf4j.sail.{NotifyingSail, NotifyingSailConnection, UpdateContext}
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.persistence.RdfLogManager

import scala.collection.mutable.ArrayBuffer

/** Logs committed RDF changes by named graph. Recording is enabled only after startup replay. */
final class RdfLoggingSail(inner: NotifyingSail, logs: RdfLogManager)
	extends NotifyingSailWrapper(inner):

	@volatile private var recordingEnabled = false

	def enableRecording(): Unit = recordingEnabled = true

	override def getConnection(): NotifyingSailConnection =
		RdfLoggingSailConnection(super.getConnection(), logs, getValueFactory, () => recordingEnabled)

end RdfLoggingSail

private final class RdfLoggingSailConnection(
	inner: NotifyingSailConnection,
	logs: RdfLogManager,
	factory: ValueFactory,
	recordingEnabled: () => Boolean
) extends NotifyingSailConnectionWrapper(inner):

	private val pending = ArrayBuffer.empty[(RdfLogManager.Binding, RdfUpdate)]

	override def begin(): Unit =
		pending.clear()
		super.begin()

	override def begin(level: IsolationLevel): Unit =
		pending.clear()
		super.begin(level)

	override def commit(): Unit =
		try
			if recordingEnabled() then
				pending.groupMap(_._1)( _._2).foreach: (binding, updates) =>
					binding.log.appendAll(updates)
			super.commit()
		finally pending.clear()

	override def rollback(): Unit =
		try super.rollback()
		finally pending.clear()

	override def addStatement(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		super.addStatement(subj, pred, obj, contexts*)
		record(subj, pred, obj, assertion = true, contexts)

	override def addStatement(modify: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		super.addStatement(modify, subj, pred, obj, contexts*)
		record(subj, pred, obj, assertion = true, contexts)

	override def removeStatements(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		val removed = matchingStatements(subj, pred, obj, contexts)
		super.removeStatements(subj, pred, obj, contexts*)
		removed.foreach(record(_, assertion = false))

	override def removeStatement(modify: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit =
		val removed = matchingStatements(subj, pred, obj, contexts)
		super.removeStatement(modify, subj, pred, obj, contexts*)
		removed.foreach(record(_, assertion = false))

	override def clear(contexts: Resource*): Unit =
		val removed = matchingStatements(null, null, null, contexts)
		super.clear(contexts*)
		removed.foreach(record(_, assertion = false))

	private def matchingStatements(
		subj: Resource | Null,
		pred: IRI | Null,
		obj: Value | Null,
		contexts: Seq[Resource]
	): IndexedSeq[Statement] =
		val iter = inner.getStatements(subj, pred, obj, false, contexts*)
		try
			val result = ArrayBuffer.empty[Statement]
			while iter.hasNext do result += iter.next()
			result.toIndexedSeq
		finally iter.close()

	private def record(
		subj: Resource,
		pred: IRI,
		obj: Value,
		assertion: Boolean,
		contexts: Seq[Resource]
	): Unit = contexts.foreach:
		case context: IRI =>
			logs.binding(context).foreach: binding =>
				pending += binding -> RdfUpdate(factory.createStatement(subj, pred, obj), assertion)
		case _ => ()

	private def record(statement: Statement, assertion: Boolean): Unit = statement.getContext match
		case context: IRI =>
			logs.binding(context).foreach: binding =>
				val triple = factory.createStatement(
					statement.getSubject,
					statement.getPredicate,
					statement.getObject
				)
				pending += binding -> RdfUpdate(triple, assertion)
		case _ => ()

end RdfLoggingSailConnection
