package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunction
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor
import org.eclipse.rdf4j.query.algebra.{StatementPattern, TupleFunctionCall, ValueConstant}
import org.eclipse.rdf4j.sail.SailException

class SimpleTupleFunctionExprEnricher(funcs: Map[String, TupleFunction]) extends AbstractQueryModelVisitor[SailException]{

	override def meet(sp: StatementPattern): Unit = {

		val pred = sp.getPredicateVar
		val obj = sp.getObjectVar

		if(pred.hasValue  && funcs.contains(pred.getValue.stringValue)){
			val funUri = pred.getValue.stringValue
			if(obj.hasValue){
				val fcall = new TupleFunctionCall
				fcall.addResultVar(sp.getSubjectVar)
				fcall.addArg(new ValueConstant(obj.getValue))
				fcall.setURI(funUri)
				sp.replaceWith(fcall)
			}
		}
	}
}
