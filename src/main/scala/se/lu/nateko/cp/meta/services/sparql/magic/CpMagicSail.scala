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

import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DataObjectFetchPatternSearch
import se.lu.nateko.cp.meta.services.sparql.magic.stats._
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner

class CpMagicSail(baseSail: NativeOrMemoryStore, init: Sail => IndexHandler) extends NotifyingSailWrapper(baseSail){

	private var indexh: IndexHandler = _

	baseSail.setEvaluationStrategyFactory{
		val tupleFunctionReg = new TupleFunctionRegistry()
		val indexThunk = () => indexh.index
		tupleFunctionReg.add(new StatsTupleFunction(indexThunk))
		new CpEvaluationStrategyFactory(tupleFunctionReg, baseSail.getFederatedServiceResolver, indexThunk)
	}

	override def initialize(): Unit = {
		super.initialize()
		indexh = init(baseSail)
	}

	override def getConnection(): NotifyingSailConnection = new NotifyingSailConnectionWrapper(baseSail.getConnection){

		getWrappedConnection.addConnectionListener(indexh)

		override def evaluate(
			tupleExpr: TupleExpr,
			dataset: Dataset,
			bindings: BindingSet,
			includeInferred: Boolean
		): CloseableIteration[_ <: BindingSet, QueryEvaluationException] = {

			val expr: TupleExpr = TupleExprCloner.cloneExpr(tupleExpr)
			expr.visit(new StatsQueryModelVisitor)

			val dofps = new DataObjectFetchPatternSearch(new CpmetaVocab(baseSail.getValueFactory))
			dofps.search(expr).foreach(_.fuse())

			try{
				getWrappedConnection.evaluate(expr, dataset, bindings, includeInferred)
			} catch{
				case iae: IllegalArgumentException =>
					iae.printStackTrace()
					throw iae
			}
		}
	}
}
