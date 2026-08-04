package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import org.eclipse.rdf4j.query.algebra.evaluation.optimizer.{OrderLimitOptimizer, QueryJoinOptimizer, StandardQueryOptimizerPipeline}
import org.eclipse.rdf4j.query.algebra.evaluation.{EvaluationStrategy, QueryOptimizer, QueryOptimizerPipeline, TripleSource}

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
