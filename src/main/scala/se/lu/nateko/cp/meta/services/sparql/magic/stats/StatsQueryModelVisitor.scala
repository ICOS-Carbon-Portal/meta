package se.lu.nateko.cp.meta.services.sparql.magic.stats

import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor
import org.eclipse.rdf4j.sail.SailException
import org.eclipse.rdf4j.query.algebra.StatementPattern
import org.eclipse.rdf4j.query.algebra.TupleFunctionCall
import org.eclipse.rdf4j.query.algebra.ValueConstant
import org.eclipse.rdf4j.query.algebra.Var
import org.eclipse.rdf4j.query.algebra.ValueExpr
import org.eclipse.rdf4j.query.algebra.Join
import org.eclipse.rdf4j.query.algebra.QueryModelNode
import org.eclipse.rdf4j.model.vocabulary.OWL

class StatsQueryModelVisitor extends AbstractQueryModelVisitor[SailException]{

	override def meet(sp: StatementPattern): Unit = {

		val pred = sp.getPredicateVar

		if(pred.hasValue  && pred.getValue.stringValue == StatsTupleFunction.hasStatProps){

			val fcall = new TupleFunctionCall
			fcall.setURI(StatsTupleFunction.hasStatProps)

			val statBlock = sp.getParentNode

			for((varr, expr) <- getStatVarsAndArgs(sp.getObjectVar, statBlock)){
				fcall.addResultVar(varr)
				fcall.addArg(expr)
			}

			val subj = sp.getSubjectVar
			if(!subj.isAnonymous){
				fcall.addResultVar(subj)
				fcall.addArg(new ValueConstant(OWL.SAMEAS))
			}
			statBlock.replaceWith(fcall)
		}
	}

	private def getStatVarsAndArgs(statVar: Var, expr: QueryModelNode): Iterator[(Var, ValueExpr)] = expr match {

		case sp: StatementPattern if sp.getSubjectVar.getName == statVar.getName =>
			val pred = sp.getPredicateVar
			if(pred.hasValue)
				Iterator(sp.getObjectVar -> new ValueConstant(pred.getValue))
			else Iterator.empty

		case join: Join =>
			getStatVarsAndArgs(statVar, join.getLeftArg) ++ getStatVarsAndArgs(statVar, join.getRightArg)

		case _ => Iterator.empty
	}
}