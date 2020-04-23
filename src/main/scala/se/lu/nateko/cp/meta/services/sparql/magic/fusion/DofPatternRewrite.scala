package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import se.lu.nateko.cp.meta.services.sparql.index.`package`.DobjUri
import org.eclipse.rdf4j.query.algebra.SingletonSet
import org.eclipse.rdf4j.query.algebra.BinaryTupleOperator
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.Slice
import org.eclipse.rdf4j.query.algebra.Order
import org.eclipse.rdf4j.query.algebra.Group
import org.eclipse.rdf4j.query.algebra.Filter
import org.eclipse.rdf4j.query.algebra.UnaryTupleOperator

object DofPatternRewrite{

	def rewrite(queryTop: TupleExpr, fusions: Seq[FusionResult]): Unit = {

		for(
			fusion <- fusions;
			exprs = fusion.exprsToFuse if !exprs.isEmpty;
			propVars = fusion.propVars.map{case (qvar, prop) => (prop, qvar.name)};
			dobjVar <- propVars.get(DobjUri).toSeq
		){

			val subsumingParents = exprs.filter{
				case _: BinaryTupleOperator => true
				case _ => false
			}

			val independentChildren = exprs.filter{expr =>
				!subsumingParents.exists(parent => parent != expr && parent.isAncestorOf(expr))
			}

			val deepest = independentChildren.toSeq.sortBy(nodeDepth).last

			exprs.filter(_ ne deepest).foreach(replaceNode)

			val fetchExpr = new DataObjectFetchNode(dobjVar, fusion.fetch, propVars)

			deepest.replaceWith(fetchExpr)
		}

		DanglingCleanup.clean(queryTop)
	}

	def replaceNode(node: TupleExpr): Unit = node match{
		case slice: Slice => slice.setOffset(0)
		case o:Order => replaceUnaryOp(o)
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
