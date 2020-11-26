package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.reflectiveCalls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunction
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.query.algebra.evaluation.impl.AbstractEvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.impl.TupleFunctionEvaluationStrategy
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper
import org.eclipse.rdf4j.query.algebra.QueryModelVisitor
import org.eclipse.rdf4j.sail.SailException

import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner

trait MagicTupleFuncPlugin extends SailConnectionListener{
	def makeFunctions: Seq[TupleFunction]
	def initialize(fromSail: Sail): Unit
	def expressionEnricher: QueryModelVisitor[SailException]
}

class MagicTupleFuncSail(plugins: Seq[MagicTupleFuncPlugin], baseSail: NativeOrMemoryStore) extends NotifyingSailWrapper(baseSail){

	baseSail.setEvaluationStrategyFactory{
		val fedResolver = baseSail.getFederatedServiceResolver()
		val tupleFunctionReg = new TupleFunctionRegistry()
		plugins.flatMap(_.makeFunctions).foreach(tupleFunctionReg.add)

		new AbstractEvaluationStrategyFactory{
			override def createEvaluationStrategy(dataSet: Dataset, tripleSrc: TripleSource) =
				new TupleFunctionEvaluationStrategy(tripleSrc, dataSet, fedResolver, tupleFunctionReg)
		}
	}

	override def initialize(): Unit = {
		super.initialize()
		plugins.foreach(_.initialize(baseSail))
	}

	override def getConnection(): NotifyingSailConnection = new NotifyingSailConnectionWrapper(baseSail.getConnection){

		plugins.foreach(getWrappedConnection.addConnectionListener)

		override def evaluate(
			tupleExpr: TupleExpr,
			dataset: Dataset,
			bindings: BindingSet,
			includeInferred: Boolean
		): CloseableIteration[_ <: BindingSet, QueryEvaluationException] = {

			val expr: TupleExpr = TupleExprCloner.cloneExpr(tupleExpr)
			plugins.foreach(plugin => expr.visit(plugin.expressionEnricher))

			getWrappedConnection.evaluate(expr, dataset, bindings, includeInferred)
		}
	}
}
