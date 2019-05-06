package se.lu.nateko.cp.meta.services.sparql.magic

import scala.annotation.tailrec
import org.eclipse.rdf4j.query.algebra._

package object fusion{

	def splitTriple(sp: StatementPattern) = (sp.getSubjectVar, sp.getPredicateVar, sp.getObjectVar)

	def highestJoinOnlyAncestor(node: QueryModelNode): Option[Join] = {
		def strict(node: QueryModelNode): Option[Join] = node match {
			case join: Join =>
				strict(join.getParentNode).orElse(Some(join))
			case _ => None
		}
		strict(node.getParentNode)
	}

	def areWithinCommonJoin(nodes: Seq[QueryModelNode]): Boolean = {
		val (filters, nonFilters) = nodes.partition(_.isInstanceOf[Filter])
		val joins = nonFilters.map(highestJoinOnlyAncestor).flatten

		joins.length == nonFilters.length &&
		joins.distinct.length == 1 &&
		filters.forall(
			filter => nonFilters.forall(filter.isAncestorOf)
		)
	}

	def areSiblings(n1: QueryModelNode, n2: QueryModelNode): Boolean = {
		n1 != null && n2 != null && (n1 ne n2) && {
			val p1 = n1.getParentNode
			p1 != null && (p1 eq n2.getParentNode)
		}
	}

	def nodeDepth(node: QueryModelNode): Int = {
		var res = -1
		var ancestor = node
		while(ancestor != null){
			ancestor = ancestor.getParentNode
			res += 1
		}
		res
	}

	implicit class EnhancedQueryModelNode(val node: QueryModelNode) extends AnyVal{
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
}
