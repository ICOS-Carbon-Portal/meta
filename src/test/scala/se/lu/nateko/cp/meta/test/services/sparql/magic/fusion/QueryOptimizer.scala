package se.lu.nateko.cp.meta.services.sparql.magic.fusion
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.impl.EmptyBindingSet
import org.eclipse.rdf4j.query.impl.SimpleDataset
import org.eclipse.rdf4j.query.algebra.evaluation.impl.*

object QueryOptimizer{

	def optimize(query: TupleExpr): Unit = {
		val dataset = new SimpleDataset
		val bindings = new EmptyBindingSet

		Seq( //taken from SailSourceConnection.evaluateInternal
			new BindingAssigner,
			//new ConstantOptimizer(strategy),
			new CompareOptimizer(),
			new ConjunctiveConstraintSplitter(),
			new DisjunctiveConstraintOptimizer(),
			new SameTermFilterOptimizer(),
			new QueryModelNormalizer(),
			//new QueryJoinOptimizer(sailStore.getEvaluationStatistics()),
			new IterativeEvaluationOptimizer(),
			new FilterOptimizer()
			//new OrderLimitOptimizer()
		).foreach(_.optimize(query, dataset, bindings))
	}
}
