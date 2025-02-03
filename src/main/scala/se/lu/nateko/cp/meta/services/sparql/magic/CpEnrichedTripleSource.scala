package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource

import StatementsEnricher.StatIter

class CpEnrichedTripleSource(base: TripleSource, enricher: StatementsEnricher) extends TripleSource{

	override def getStatements(subj: Resource, pred: IRI, obj: Value, ctxts: Resource*): StatIter =
		enricher.enrich(base.getStatements(subj, pred, obj, ctxts*), subj, pred, obj)


	override def getValueFactory(): ValueFactory = base.getValueFactory
}
