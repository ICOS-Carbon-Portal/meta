package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.AbstractQueryModelNode
import org.eclipse.rdf4j.query.algebra.QueryModelNode
import org.eclipse.rdf4j.query.algebra.QueryModelVisitor
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.Property
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch.GroupPattern

import scala.jdk.CollectionConverters.SeqHasAsJava

// Extending TupleExpr with this class does not work due to conflict of clone() methods
// but interestingly it works in the concrete classes extending this one
abstract class MagicFetchNode extends AbstractQueryModelNode:

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match
		case _: EvaluationStatistics.CardinalityCalculator => // this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)


	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {}
	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {}

	protected def mkSet(strs: Seq[String]): java.util.Set[String] = new java.util.HashSet[String](strs.asJava)

end MagicFetchNode


final case class KeywordsFetchNode(
	bindingName: String, inner: DataObjectFetchNode
) extends MagicFetchNode with TupleExpr {

	private val assuredVars: Seq[String] = Seq(bindingName)

	override def clone() = new KeywordsFetchNode(
		bindingName,
		inner.clone() match {
			case clone: DataObjectFetchNode => clone
		}
	)

	override def getAssuredBindingNames() = mkSet(assuredVars)
	override def getBindingNames() = mkSet(assuredVars)
	override def getSignature(): String = s"KeywordsFetchNode(${inner.getSignature()})"

}


class DataObjectFetchNode(
	val fetchRequest: DataObjectFetch, val varNames: Map[Property, String]
) extends MagicFetchNode with TupleExpr{

	private val allVars = varNames.values.toIndexedSeq

	override def clone() = new DataObjectFetchNode(fetchRequest, varNames)

	override def getAssuredBindingNames() = getBindingNames()
	override def getBindingNames() = mkSet(allVars)

	override def getSignature(): String = {
		val orig = super.getSignature
		val filters = s"filter: ${fetchRequest.filter.toString}"
		val vars = s"""vars: ${allVars.mkString(", ")}"""
		val sorting = fetchRequest.sort.fold("no sort")(sb => {
			val dir = if(sb.descending) "DESC" else "ASC"
			s"order by $dir(${sb.property})"
		})
		val offset = s"offset ${fetchRequest.offset}"
		s"$orig ($vars), $sorting, $offset, $filters"
	}

}


class StatsFetchNode(val countVarName: String, val group: GroupPattern) extends MagicFetchNode with TupleExpr{

	private val assuredVars: Seq[String] = Seq(countVarName, group.submitterVar, group.specVar) ++ group.siteVar

	override def clone() = new StatsFetchNode(countVarName, group)

	override def getAssuredBindingNames() = mkSet(assuredVars)
	override def getBindingNames() = mkSet(assuredVars :+ group.stationVar)


	override def getSignature(): String = s"StatsFetchNode($countVarName, ${group.submitterVar}, ${group.stationVar}, ${group.specVar})"

}
