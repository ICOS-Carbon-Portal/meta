package se.lu.nateko.cp.meta.services.geosparql

import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor
import org.eclipse.rdf4j.sail.SailException
import org.eclipse.rdf4j.query.algebra.StatementPattern
import org.eclipse.rdf4j.query.algebra.TupleFunctionCall
import org.eclipse.rdf4j.query.algebra.ValueConstant

class IcosGeoSparqlSailConnection(baseConn: NotifyingSailConnection, geoIndex: IcosGeoIndex)
		extends NotifyingSailConnectionWrapper(baseConn){

	baseConn.addConnectionListener(new GeoIndexUpdatingConnectionListener(geoIndex))

	override def evaluate(tupleExpr: TupleExpr, dataset: Dataset, bindings: BindingSet, includeInferred: Boolean):
		CloseableIteration[_ <: BindingSet, QueryEvaluationException] =
	{
		val expr: TupleExpr = TupleExprCloner.cloneExpr(tupleExpr)
		expr.visit(GeoTupleFunctionExprEnricher)

		baseConn.evaluate(expr, dataset, bindings, includeInferred)
	}

}


object GeoTupleFunctionExprEnricher extends AbstractQueryModelVisitor[SailException]{

	override def meet(sp: StatementPattern): Unit = {
		val pred = sp.getPredicateVar
		val obj = sp.getObjectVar
		if(pred.hasValue && obj.hasValue && pred.getValue.stringValue == GeoConstants.testFun){
			val fcall = new TupleFunctionCall
			fcall.addResultVar(sp.getSubjectVar)
			fcall.addArg(new ValueConstant(obj.getValue))
			fcall.setURI(GeoConstants.testFun)
			sp.replaceWith(fcall)
		}
	}
}
