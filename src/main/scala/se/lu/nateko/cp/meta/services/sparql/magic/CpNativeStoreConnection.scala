package org.eclipse.rdf4j.sail.nativerdf

import org.eclipse.rdf4j.model.*
import se.lu.nateko.cp.meta.services.sparql.magic.StatementsEnricher
import StatementsEnricher.StatIter

class CpNativeStoreConnection(sail: NativeStore, enricher: StatementsEnricher) extends NativeStoreConnection(sail){

	override def getStatementsInternal(subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*): StatIter = {

		val base = super.getStatementsInternal(subj, pred, obj, includeInferred, contexts*)

		enricher.enrich(base, subj, pred, obj)

	}

}
