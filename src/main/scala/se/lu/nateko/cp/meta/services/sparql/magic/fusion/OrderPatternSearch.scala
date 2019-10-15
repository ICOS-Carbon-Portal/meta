package se.lu.nateko.cp.meta.services.sparql.magic.fusion
import org.eclipse.rdf4j.query.algebra.Order
import org.eclipse.rdf4j.query.algebra.Var

object OrderPatternSearch{
	import PatternFinder._
	import DataObjectFetchPattern.OrderPattern

	val search: TopNodeSearch[OrderPattern] = takeNode
		.ifIs[Order]
		.thenSearch{order =>

			val elems = order.getElements

			if(elems.size == 1){
				val elem = elems.get(0)

				Some(elem.getExpr).collect{
					case v: Var => new OrderPattern(order, v.getName, !elem.isAscending)
				}
			}
			else None
		}
}
