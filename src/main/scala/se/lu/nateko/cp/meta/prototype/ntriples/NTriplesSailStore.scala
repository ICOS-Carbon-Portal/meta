package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import java.io.File
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import org.eclipse.rdf4j.sail.base.{SailSource, SailStore}
import org.eclipse.rdf4j.model.ValueFactory

class NTriplesSailStore(dataFile: File) extends SailStore {

	private val valueFactory = SimpleValueFactory.getInstance()
	private val explicitSource = new NTriplesSailSource(this, explicit = true)
	private val inferredSource = new NTriplesSailSource(this, explicit = false)

	override def getValueFactory: ValueFactory = valueFactory
	override def getExplicitSailSource: SailSource = explicitSource
	override def getInferredSailSource: SailSource = inferredSource
	override def getEvaluationStatistics: EvaluationStatistics = new EvaluationStatistics()

	override def close(): Unit = {}
}
