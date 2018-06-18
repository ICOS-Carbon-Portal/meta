package se.lu.nateko.cp.meta.services.sparql

import org.eclipse.rdf4j.sail.NotifyingSail
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver

package object magic {
	type NativeOrMemoryStore = NotifyingSail{
		def setEvaluationStrategyFactory(f: EvaluationStrategyFactory): Unit;
		def getFederatedServiceResolver(): FederatedServiceResolver
	}
}
