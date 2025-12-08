package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.query.algebra.evaluation.impl.DefaultEvaluationStrategyFactory
import org.eclipse.rdf4j.sail.SailException
import org.eclipse.rdf4j.sail.base.SailSourceConnection

class NTriplesSailConnection(
	sail: NTriplesSail,
	store: NTriplesSailStore
) extends SailSourceConnection(
	sail,
	store,
	new DefaultEvaluationStrategyFactory()
) {

	@throws[SailException]
	override protected def addStatementInternal(
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Resource*
	): Unit = {
		// This method is called when statements are added
		// The actual addition is handled by the SailStore/SailSink
		// This is just for notification purposes if needed
	}

	@throws[SailException]
	override protected def removeStatementsInternal(
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Resource*
	): Unit = {
		// This method is called when statements are removed
		// The actual removal is handled by the SailStore/SailSink
		// This is just for notification purposes if needed
	}
}
