package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.reflect.ClassTag

import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor

class IcosDataObjFetchExpr(val specVarName: String, val dobjVarName: String) extends AbstractQueryModelNode with TupleExpr{
	override def clone() = new IcosDataObjFetchExpr(specVarName, dobjVarName)

	def visit[X <: Exception](v: QueryModelVisitor[X]): Unit = ???
	def getAssuredBindingNames(): java.util.Set[String] = ???
	def getBindingNames(): java.util.Set[String] = ???
}

object PatternFinder{
	type QMN = QueryModelNode
	type NodeSearch[-I <: QMN, +O] = I => Option[O]
	type TopNodeSearch[+O] = NodeSearch[QMN, O]

	class Visitor[T](test: TopNodeSearch[T]) extends AbstractQueryModelVisitor{

		var result: Option[T] = None

		override def meetNode(node: QueryModelNode): Unit = {
			val res = test(node)
			if(res.isDefined) result = res
			else node.visitChildren(this)
		}
	}

	def recursiveSearch[T](test: TopNodeSearch[T]): TopNodeSearch[T] = node => {
		val finder = new Visitor(test)
		node.visit(finder)
		finder.result
	}

	implicit class NodeSearchOps[I <: QMN, O <: QMN](val test: NodeSearch[I, O]) extends AnyVal{

		def thenSearch[O2](other: NodeSearch[O, O2]): NodeSearch[I, O2] = node => test(node).flatMap(other)

		def thenGet[O2](f: O => O2): NodeSearch[I, O2] = test.andThen(_.map(f).filter(_ != null))

		def ifIs[O2 : ClassTag]: NodeSearch[I, O2] = thenSearch{
			case t: O2 => Some(t)
			case _ => None
		}

		def ifFound[O2](other: NodeSearch[O, O2]): NodeSearch[I, O] = node => test(node).filter(t => other(t).isDefined)
	}

	def takeNode[T <: QMN]: NodeSearch[T, T] = Some.apply
}

object DataObjFetchPatternFinder{
	import PatternFinder._

	val isLatestDobjVersionFilter: TopNodeSearch[Filter] = takeNode.ifIs[Filter].ifFound(
		takeNode[Filter].thenGet(_.getCondition).ifIs[Not].thenGet(_.getArg).ifIs[Exists]
			.thenGet(_.getSubQuery).ifIs[StatementPattern]
	)

	def findLatestDobjVersionFilter(expr: TupleExpr): Option[Filter] = recursiveSearch(isLatestDobjVersionFilter)(expr)
}