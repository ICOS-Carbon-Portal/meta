package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.language.unsafeNulls

import org.eclipse.rdf4j.query.algebra.{BinaryTupleOperator, Filter, Group, Order, SingletonSet, Slice, TupleExpr, UnaryTupleOperator, QueryModelNode}

object DofPatternRewrite{

	def rewrite(queryTop: TupleExpr, fusions: Seq[FusionPattern]): Unit = fusions.foreach{
		case UniqueKeywordsFusion(bindingName, expression, fusion) => {
			rewriteForDobjListFetches(expression, fusion) match {
				case Some(node) =>
					expression.replaceWith(UniqueKeywordsNode(bindingName, node))
				case None =>
					throw new Exception("Body of unique keywords query must be a magic data object query")
			}
		}
		case dlf: DobjListFusion => rewriteForDobjListFetches(queryTop, dlf)
		case DobjStatFusion(expr, statsNode) =>
			expr.getArg.replaceWith(statsNode)
			expr.getElements.removeIf(elem => StatsFetchPatternSearch.singleVarCount(elem.getExpr).isDefined)
	}

	def rewriteForDobjListFetches(queryTop: TupleExpr, fusion: DobjListFusion): Option[DataObjectFetchNode] = if(!fusion.exprsToFuse.isEmpty){
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
		Some(fetchExpr)
	}
	else {
		None
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
