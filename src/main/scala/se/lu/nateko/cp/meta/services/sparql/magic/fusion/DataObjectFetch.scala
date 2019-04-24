package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics

class DataObjectFetch(hasObjSpec: StatementPattern, val excludeDeprecated: Boolean) extends AbstractQueryModelNode with TupleExpr{

	val dobjVarName = hasObjSpec.getSubjectVar.getName
	val specVarName = hasObjSpec.getObjectVar.getName

	override def clone() = new DataObjectFetch(hasObjSpec, excludeDeprecated)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator =>
		case _ => v.meetOther(this)
	}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = hasObjSpec.visit(v)

	override def getAssuredBindingNames(): java.util.Set[String] = hasObjSpec.getAssuredBindingNames
	override def getBindingNames(): java.util.Set[String] = hasObjSpec.getBindingNames

	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}

	override def getSignature(): String = s"${super.getSignature} (exclude deprecated: ${excludeDeprecated})"

}
