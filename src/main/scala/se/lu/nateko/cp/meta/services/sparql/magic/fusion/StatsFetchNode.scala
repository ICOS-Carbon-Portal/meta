package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.collection.JavaConverters.asJavaCollectionConverter
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch.GroupPattern

class StatsFetchNode(
	val countVarName: String,
	val group: GroupPattern
) extends AbstractQueryModelNode with TupleExpr{

	override def clone() = new StatsFetchNode(countVarName, group)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator => //this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)
	}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {}

	override def getAssuredBindingNames(): java.util.Set[String] = getBindingNames
	override def getBindingNames() = new java.util.HashSet[String](
		Seq(countVarName, group.submitterVar, group.stationVar, group.specVar).asJavaCollection
	)

	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}

	override def getSignature(): String = s"StatsFetchNode($countVarName, ${group.submitterVar}, ${group.stationVar}, ${group.specVar})"
}
