package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.jdk.CollectionConverters.SeqHasAsJava
import org.eclipse.rdf4j.query.algebra.*
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch.GroupPattern

class StatsFetchNode(
	val countVarName: String,
	val group: GroupPattern
) extends AbstractQueryModelNode with TupleExpr{

	private val assuredVars: Seq[String] = Seq(countVarName, group.submitterVar, group.specVar) ++ group.siteVar

	override def clone() = new StatsFetchNode(countVarName, group)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator => //this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)
	}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {}

	override def getAssuredBindingNames() = mkSet(assuredVars)
	override def getBindingNames() = mkSet(assuredVars :+ group.stationVar)

	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}

	override def getSignature(): String = s"StatsFetchNode($countVarName, ${group.submitterVar}, ${group.stationVar}, ${group.specVar})"

	private def mkSet(strs: Seq[String]): java.util.Set[String] = new java.util.HashSet[String](strs.asJava)
}
