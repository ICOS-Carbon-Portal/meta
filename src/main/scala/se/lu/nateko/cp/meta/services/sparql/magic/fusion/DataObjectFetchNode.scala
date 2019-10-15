package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.collection.JavaConverters.asJavaCollectionConverter
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.Property

class DataObjectFetchNode(
	val fetchRequest: DataObjectFetch,
	val varNames: Map[Property, String]
) extends AbstractQueryModelNode with TupleExpr{

	private val allVars = varNames.values.toIndexedSeq

	override def clone() = new DataObjectFetchNode(fetchRequest, varNames)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator => //this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)
	}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {}

	override def getAssuredBindingNames(): java.util.Set[String] = getBindingNames
	override def getBindingNames() = new java.util.HashSet[String](allVars.asJavaCollection)


	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}

	override def getSignature(): String = s"${super.getSignature} (vars: ${allVars.mkString(", ")}; exclude deprecated: ${
		fetchRequest.filtering.filterDeprecated
	})"

}
