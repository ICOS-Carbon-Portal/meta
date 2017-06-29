package se.lu.nateko.cp.meta.services.geosparql

import org.eclipse.rdf4j.query.algebra.evaluation.impl.AbstractEvaluationStrategyFactory
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver
import org.eclipse.rdf4j.query.algebra.evaluation.impl.TupleFunctionEvaluationStrategy

class IcosGeoEvaluationStrategyFactory(geoIndex: IcosGeoIndex, serviceRes: FederatedServiceResolver) extends AbstractEvaluationStrategyFactory{

	private val tupleFunctionReg = {
		val reg = new TupleFunctionRegistry()
		reg.add(new TestTupleFunction(geoIndex))
		reg
	}
	
	override def createEvaluationStrategy(dataSet: Dataset, tripleSrc: TripleSource) =
		new TupleFunctionEvaluationStrategy(tripleSrc, dataSet, serviceRes, tupleFunctionReg)
}
