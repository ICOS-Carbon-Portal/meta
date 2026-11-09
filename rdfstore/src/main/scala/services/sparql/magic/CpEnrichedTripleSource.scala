package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import se.lu.nateko.cp.meta.services.sparql.QueryCancellation

import StatementsEnricher.StatIter

class CpEnrichedTripleSource(base: TripleSource, enricher: StatementsEnricher) extends TripleSource{

	override def getStatements(subj: Resource, pred: IRI, obj: Value, ctxts: Resource*): StatIter = {
		QueryCancellation.check()
		CancellableStatIter(enricher.enrich(base.getStatements(subj, pred, obj, ctxts*), subj, pred, obj))
	}


	override def getValueFactory(): ValueFactory = base.getValueFactory
}

/** Lets a cancelled SPARQL query stop in the middle of a long scan, see QueryCancellation */
private class CancellableStatIter(inner: StatIter) extends CloseableIteration[Statement]{

	override def hasNext(): Boolean = {
		QueryCancellation.check()
		inner.hasNext()
	}

	override def next(): Statement = inner.next()
	override def remove(): Unit = inner.remove()
	override def close(): Unit = inner.close()
}
