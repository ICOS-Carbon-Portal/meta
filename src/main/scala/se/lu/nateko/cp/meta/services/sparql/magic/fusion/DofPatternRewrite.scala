package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.{BinaryTupleOperator, Filter, Group, Order, SingletonSet, Slice, TupleExpr, UnaryTupleOperator}
import org.eclipse.rdf4j.query.algebra.QueryModelNode
import org.eclipse.rdf4j.query.algebra.QueryModelVisitor
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import scala.jdk.CollectionConverters.SeqHasAsJava
import org.eclipse.rdf4j.query.algebra.AbstractQueryModelNode

object DofPatternRewrite{

	def rewrite(queryTop: TupleExpr, fusions: Seq[FusionPattern]): Unit =
		fusions.foreach{
			case UniqueKeywordsFusion(bindingName, expression, fusion) => {
				val Some(node) = rewriteForDobjListFetches(expression, fusion) : @unchecked
				expression.replaceWith(KeywordsNode(bindingName, node))
			}
		case dlf: DobjListFusion => rewriteForDobjListFetches(queryTop, dlf)
		case DobjStatFusion(expr, statsNode) =>
			expr.getArg.replaceWith(statsNode)
			expr.getElements.removeIf(elem => StatsFetchPatternSearch.singleVarCount(elem.getExpr).isDefined)
	}

	def rewriteForDobjListFetches(queryTop: TupleExpr, fusion: DobjListFusion) : Option[DataObjectFetchNode] = if(!fusion.exprsToFuse.isEmpty){
		import fusion.{exprsToFuse => exprs}

		val subsumingParents = exprs.collect{case bto: BinaryTupleOperator => bto}

		val independentChildren = exprs.filter{expr =>
			!subsumingParents.exists(parent => parent != expr && parent.isAncestorOf(expr)) &&
			expr.getParentNode != null
		}

		val deepest = independentChildren.toSeq.sortBy(weightedNodeDepth).last

		exprs.filter(_ ne deepest).foreach(replaceNode)

		val propVars = fusion.propVars.collect{case (qvar, prop) => (prop, qvar.name)}
		val fetchExpr = new DataObjectFetchNode(fusion.fetch, propVars)

		safelyReplace(deepest, fetchExpr)

		DanglingCleanup.clean(queryTop)

		return Some(fetchExpr)
	} else {
		return None
	}

	def replaceNode(node: TupleExpr): Unit = node match{
		case slice: Slice => slice.setOffset(0)
		case o: Order => replaceUnaryOp(o)
		case g: Group => replaceUnaryOp(g)
		case f: Filter => replaceUnaryOp(f)

		case _: UnaryTupleOperator =>

		case _ =>
			safelyReplace(node, new SingletonSet)
	}

	private def replaceUnaryOp(op: UnaryTupleOperator): Unit = safelyReplace(op, op.getArg)

	def safelyReplace(expr: TupleExpr, replacement: TupleExpr): Unit = {
		val parent = expr.getParentNode
		if(parent != null){
			parent.replaceChildNode(expr, replacement)
			expr.setParentNode(null)
		}
	}
}

// TODO: Find a away round using a completely custom node, or actually thiknk about each method.
final case class KeywordsNode(bindingName: String, inner: DataObjectFetchNode) extends AbstractQueryModelNode with TupleExpr {
	private val assuredVars: Seq[String] = Seq() // TODO: ???

	override def clone() = new KeywordsNode(
		bindingName,
		inner.clone() match {
			case clone: DataObjectFetchNode => clone
		}
	)

	override def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = v match {
		case _: EvaluationStatistics.CardinalityCalculator => // this visitor crashes on 'alien' query nodes
		case _ => v.meetOther(this)
	}

	override def visitChildren[X <: Exception](v: QueryModelVisitor[X]): Unit = {} // TODO: Do something?
	override def getAssuredBindingNames() = mkSet(assuredVars)
	override def getBindingNames() = mkSet(assuredVars)
	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = {} // TODO: Do something?
	override def getSignature(): String = s"KeywordsExpr($inner)"
	private def mkSet(strs: Seq[String]): java.util.Set[String] = new java.util.HashSet[String](strs.asJava)
}
