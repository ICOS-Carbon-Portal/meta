package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.*

import scala.annotation.tailrec

def splitTriple(sp: StatementPattern) = (sp.getSubjectVar, sp.getPredicateVar, sp.getObjectVar)

def highestJoinOnlyAncestor(node: QueryModelNode): Option[Join] = {
	def strict(node: QueryModelNode): Option[Join] = node match {
		case join: Join =>
			strict(join.getParentNode).orElse(Some(join))
		case _ => None
	}
	strict(node.getParentNode)
}

def highestAncestorInSameQuery(node: QueryModelNode): Option[QueryModelNode] = {
	def strict(node: QueryModelNode): Option[QueryModelNode] = node match {
		case _: Extension | _: Filter | _: Join | _: Order =>
			highestAncestorInSameQuery(node.getParentNode).orElse(Some(node))
		case _ => None
	}
	strict(node.getParentNode).orElse(strict(node))
}

def earlierEvaluatedNodesInSameQuery(node: QueryModelNode): Seq[TupleExpr] = node.getParentNode match {
	case binop: BinaryTupleOperator =>
		if(binop.getRightArg eq node) binop.getLeftArg +: earlierEvaluatedNodesInSameQuery(binop)
		else Seq.empty

	case _: Extension | _: Filter | _: Order =>
		earlierEvaluatedNodesInSameQuery(node.getParentNode)

	case _ => Seq.empty
}

def areWithinCommonJoin(nodes: Seq[QueryModelNode]): Boolean = {
	val joins = nodes.flatMap(highestJoinOnlyAncestor)
	joins.length == nodes.length && joins.distinct.length == 1
}

def areWithinSameQuery(nodes: Seq[QueryModelNode]): Boolean = {
	val joins = nodes.flatMap(highestAncestorInSameQuery)
	joins.length == nodes.length && joins.distinct.length == 1
}

def areSiblings(n1: QueryModelNode, n2: QueryModelNode): Boolean = {
	n1 != null && n2 != null && (n1 ne n2) && {
		val p1 = n1.getParentNode
		p1 != null && (p1 eq n2.getParentNode)
	}
}

def weightedNodeDepth(node: QueryModelNode): Int = {
	var res = -1
	var ancestor = node
	while(ancestor != null){
		ancestor = ancestor.getParentNode
		res += (ancestor match{
			case _: Union => 10000
			case _ => 1
		})
	}
	res
}

@tailrec
def treeTop(anyNode: QueryModelNode): QueryModelNode = {
	val parent = anyNode.getParentNode
	if(parent == null) anyNode else treeTop(parent)
}

extension (node: QueryModelNode){
	@tailrec def isAncestorOf(other: QueryModelNode): Boolean =
		if(other == null) false
		else if(other eq node) true
		else isAncestorOf(other.getParentNode)

	def isUncleOf(other: QueryModelNode): Boolean = other != null && node != null && {
		val grandParent = node.getParentNode
		val sibling = other.getParentNode
		grandParent != null && sibling != null && (sibling ne node) && (grandParent eq sibling.getParentNode)
	}
}
