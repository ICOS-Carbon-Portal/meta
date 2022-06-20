package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategy
import org.eclipse.rdf4j.query.algebra.evaluation.QueryOptimizer
import org.eclipse.rdf4j.query.algebra.evaluation.QueryOptimizerPipeline
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import org.eclipse.rdf4j.query.algebra.evaluation.impl.OrderLimitOptimizer
import org.eclipse.rdf4j.query.algebra.evaluation.impl.QueryJoinOptimizer
import org.eclipse.rdf4j.query.algebra.evaluation.impl.StandardQueryOptimizerPipeline

class CpQueryOptimizerPipeline(
	strat: EvaluationStrategy,
	tripleSource: TripleSource,
	stats: EvaluationStatistics
) extends QueryOptimizerPipeline{

	override def getOptimizers(): java.lang.Iterable[QueryOptimizer] = {
		val stdOpts = StandardQueryOptimizerPipeline(strat, tripleSource, stats).getOptimizers
		val res = java.util.ArrayList[QueryOptimizer]

		stdOpts.forEach{
			case _: QueryJoinOptimizer  => //ignore, for better determinism of query execution order
			case _: OrderLimitOptimizer => //ignore, iirc, interferes with SPARQL "magic"
			case other => res.add(other)
		}
		res
	}
}
