package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.*
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor
import scala.reflect.ClassTag

object PatternFinder{

	type QMN = QueryModelNode
	type NodeSearch[-I <: QMN, +O] = I => Option[O]
	type TopNodeSearch[+O] = NodeSearch[QMN, O]

	def takeNode[T <: QMN]: NodeSearch[T, T] = Some.apply

	extension [O] (test: TopNodeSearch[O])
		def recursive: TopNodeSearch[O] = node => {
			val finder = new Visitor(test)
			node.visit(finder)
			finder.result
		}

	extension [I <: QMN, O](test: NodeSearch[I, O]){

		def thenSearch[O2](other: O => Option[O2]): NodeSearch[I, O2] = node => test(node).flatMap(other)

		def thenAlsoSearch[O2](other: O => Option[O2]): NodeSearch[I, (O, O2)] = node => {
			for(o <- test(node); o2 <- other(o)) yield o -> o2
		}

		def thenFlatMap[O2](next: O => NodeSearch[I, O2]): NodeSearch[I, (O, O2)] =
			node => for(o1 <- test(node); o2 <- next(o1)(node)) yield o1 -> o2

		def thenGet[O2](f: O => O2): NodeSearch[I, O2] = test.andThen(_.map(f).filter(_ != null))

		def ifIs[O2 : ClassTag]: NodeSearch[I, O2] = thenSearch{
			case t: O2 => Some(t)
			case _ => None
		}

		def filter(pred: O => Boolean): NodeSearch[I, O] = test.andThen(_.filter(pred))

		def ifFound[O2](other: O => Option[O2]): NodeSearch[I, O] = node => test(node).filter(t => other(t).isDefined)

		def optional: NodeSearch[I, Option[O]] = test.andThen(Some(_))

		def orElse[I2 >: I <: QMN, O2 <: O](other: NodeSearch[I2, O2]): NodeSearch[I, O] =
			node => test(node).orElse(other(node))
	}

	private class Visitor[T](test: TopNodeSearch[T]) extends AbstractQueryModelVisitor[Exception]{

		var result: Option[T] = None

		override def meetNode(node: QueryModelNode): Unit = if(!result.isDefined){
			result = test(node)
			if(!result.isDefined) node.visitChildren(this)
		}
	}

}
