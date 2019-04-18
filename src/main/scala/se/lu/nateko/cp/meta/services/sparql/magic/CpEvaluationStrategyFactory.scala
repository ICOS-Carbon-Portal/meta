package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.algebra.evaluation.impl.AbstractEvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.impl.TupleFunctionEvaluationStrategy
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryEvaluationException
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DataObjectFetch

import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry

class CpEvaluationStrategyFactory(
	tupleFunctionReg: TupleFunctionRegistry,
	fedResolver: FederatedServiceResolver
) extends AbstractEvaluationStrategyFactory{

	override def createEvaluationStrategy(dataSet: Dataset, tripleSrc: TripleSource) =
		new TupleFunctionEvaluationStrategy(tripleSrc, dataSet, fedResolver, tupleFunctionReg){
			override def evaluate(expr: TupleExpr, bindings: BindingSet): CloseableIteration[BindingSet, QueryEvaluationException] = {
				expr match {
					case doFetch: DataObjectFetch =>
						???
					case _ => super.evaluate(expr, bindings)
				}
			}
		}

}
