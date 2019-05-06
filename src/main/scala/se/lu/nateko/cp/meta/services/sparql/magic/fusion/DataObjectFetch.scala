package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.collection.JavaConverters.asJavaCollectionConverter
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics

class DataObjectFetch(
	val dobjVar: String,
	val specVar: Option[String],
	val dataStartTimeVar: Option[String],
	val dataEndTimeVar: Option[String],
	val stationVar: Option[String],
	val excludeDeprecated: Boolean
) extends AbstractQueryModelNode with TupleExpr{

	private val allVars = Seq(dobjVar) ++ specVar ++ dataStartTimeVar ++ dataEndTimeVar ++ stationVar

	override def clone() = new DataObjectFetch(dobjVar, specVar, dataStartTimeVar, dataEndTimeVar, stationVar, excludeDeprecated)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator => //this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)
	}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {}

	override def getAssuredBindingNames(): java.util.Set[String] = getBindingNames
	override def getBindingNames() = new java.util.HashSet[String](allVars.asJavaCollection)


	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}

	override def getSignature(): String = s"${super.getSignature} (vars: ${allVars.mkString(", ")}; exclude deprecated: ${excludeDeprecated})"

}
