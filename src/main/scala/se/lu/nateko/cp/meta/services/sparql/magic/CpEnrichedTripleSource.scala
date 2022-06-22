package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value

import StatementsEnricher.StatIter
import org.eclipse.rdf4j.model.ValueFactory

class CpEnrichedTripleSource(base: TripleSource, enricher: StatementsEnricher) extends TripleSource{

	override def getStatements(subj: Resource, pred: IRI, obj: Value, ctxts: Resource*): StatIter[QueryEvaluationException] =
		enricher.enrich(base.getStatements(subj, pred, obj, ctxts*), subj, pred, obj)


	override def getValueFactory(): ValueFactory = base.getValueFactory
}
