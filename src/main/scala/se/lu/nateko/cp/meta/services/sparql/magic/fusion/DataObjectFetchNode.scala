package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.jdk.CollectionConverters.SeqHasAsJava
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.Property

class DataObjectFetchNode(
	val dobjVarName: String,
	val fetchRequest: DataObjectFetch,
	val varNames: Map[Property, String]
) extends AbstractQueryModelNode with TupleExpr{

	private val allVars = varNames.values.toIndexedSeq

	override def clone() = new DataObjectFetchNode(dobjVarName, fetchRequest, varNames)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator => //this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)
	}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {}

	override def getAssuredBindingNames(): java.util.Set[String] = getBindingNames
	override def getBindingNames() = new java.util.HashSet[String](allVars.asJava)


	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}

	override def getSignature(): String = {
		val orig = super.getSignature
		val filters = s"filters: ${fetchRequest.filter.toString}"
		val vars = s"""vars: ${allVars.mkString(", ")}"""
		val sorting = fetchRequest.sort.fold("no sort")(sb => {
			val dir = if(sb.descending) "DESC" else "ASC"
			s"order by $dir(${sb.property})"
		})
		val offset = s"offset ${fetchRequest.offset}"
		s"$orig ($vars), $sorting, $offset, $filters"
	}

}
