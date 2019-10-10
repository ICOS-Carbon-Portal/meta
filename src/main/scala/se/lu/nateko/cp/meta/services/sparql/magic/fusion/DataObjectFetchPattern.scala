package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.{Filter => _, _}
import DobjPattern._

trait DobjPattern{
	def expressions: Seq[TupleExpr]
	def removeExpressions(): Unit = expressions.foreach(_.replaceWith(new SingletonSet))
}

object DobjPattern{
	trait PropPattern extends DobjPattern{
		type ValueType
		def property: Property[ValueType]
		def propVarName: String
	}

	trait ContPropPattern extends PropPattern{ override def property: ContProp[ValueType] }

	trait CategPropPattern extends PropPattern{
		type ValueType <: AnyRef
		override def property: CategProp[ValueType]
		def categValues: Seq[ValueType]
	}

	def contPattern[T](exprs: Seq[TupleExpr], prop: ContProp[T], propVar: String) = new ContPropPattern{
		type ValueType = T
		val expressions = exprs
		val property = prop
		val propVarName = propVar
	}

	def categPattern[T <: AnyRef](exprs: Seq[TupleExpr], prop: CategProp[T], propVar: String, vals: Seq[T]) = new CategPropPattern{
		type ValueType = T
		val expressions = exprs
		val property = prop
		val propVarName = propVar
		val categValues = vals
	}

}

class ExcludeDeprecatedPattern(val expr: Filter, val dobjVar: String) extends DobjPattern{
	override def expressions = Seq(expr)
	override def removeExpressions(): Unit = expr.getParentNode.replaceChildNode(expr, expr.getArg)
}

class DataObjectFetchPattern(
	dobjVarName: String,
	categPatterns: Seq[CategPropPattern],
	propPatterns: Seq[ContPropPattern],
	noDeprecated: Option[ExcludeDeprecatedPattern]
){

	val allPatterns: Seq[DobjPattern] = categPatterns ++ propPatterns ++ noDeprecated

	def fuse(): Unit = if(!allPatterns.isEmpty){

		val fetch = new DataObjectFetch(
			selections = categPatterns.map(cp => selection(cp.property, cp.categValues)),
			filtering = new Filtering(Nil, noDeprecated.isDefined, propPatterns.map(_.property)),
			sort = None,
			offset = 0
		)

		val varNames: Map[Property[_], String] = (categPatterns ++ propPatterns).map(p => p.property -> p.propVarName).toMap

		val fetchExpr = new DataObjectFetchNode(fetch, varNames + (DobjUri -> dobjVarName))

		val deepest = allPatterns.maxBy(p => p.expressions.map(nodeDepth).max)
		val deepestExpr = deepest.expressions.maxBy(nodeDepth)
		deepest.expressions.filter(_ ne deepestExpr).foreach(_.replaceWith(new SingletonSet))
		deepestExpr.replaceWith(fetchExpr)

		for(patt <- allPatterns if patt ne deepest) patt.removeExpressions()
	}

}
