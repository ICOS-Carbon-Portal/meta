package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.{Filter => FetchFilter, _}
import DataObjectFetchPattern._

object DataObjectFetchPattern{
	sealed trait SubPattern{
		def expressions: Seq[TupleExpr]
		def removeExpressions(): Unit = expressions.foreach(_.replaceWith(new SingletonSet))
	}

	sealed abstract class UnaryTupleOpSubPattern(expr: UnaryTupleOperator) extends SubPattern{
		override def expressions = Seq(expr)
		override def removeExpressions(): Unit = expr.replaceWith(expr.getArg)
	}

	sealed trait PropPattern extends SubPattern{
		type ValueType
		def property: Property[ValueType]
		def propVarName: String
	}

	sealed trait ContPropPattern extends PropPattern{ override def property: ContProp[ValueType] }

	sealed trait CategPropPattern extends PropPattern{
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

	class ExcludeDeprecatedPattern(expr: Filter, val dobjVar: String) extends UnaryTupleOpSubPattern(expr)
	class OrderPattern(expr: Order, val sortVar: String, val descending: Boolean) extends UnaryTupleOpSubPattern(expr)
	class FilterPattern(expr: Filter, val filters: Seq[FetchFilter]) extends UnaryTupleOpSubPattern(expr)

	class OffsetPattern(expr: Slice) extends SubPattern{
		val offset = expr.getOffset.toInt
		override def expressions = Seq(expr)
		override def removeExpressions(): Unit = expr.setOffset(0)
	}
}

class DataObjectFetchPattern(
	dobjVarName: String,
	categPatterns: Seq[CategPropPattern],
	contPatterns: Seq[ContPropPattern],
	filter: Option[FilterPattern],
	noDeprecated: Option[ExcludeDeprecatedPattern],
	singleVarOrder: Option[OrderPattern],
	offset: Option[OffsetPattern]
){

	private val sortBy: Option[SortBy] = singleVarOrder.flatMap{orderPatt =>
		contPatterns.collectFirst{
			case cpp if cpp.propVarName == orderPatt.sortVar => SortBy(cpp.property, orderPatt.descending)
		}
	}

	private val order: Option[OrderPattern] = singleVarOrder.filter(_ => sortBy.isDefined)

	val allPatterns: Seq[SubPattern] = categPatterns ++ contPatterns ++ filter ++ noDeprecated ++ order ++ offset

	def fuse(): Unit = if(!allPatterns.isEmpty){

		val filters = filter.fold[Seq[FetchFilter]](Nil)(_.filters)

		val fetch = new DataObjectFetch(
			selections = categPatterns.map(cp => selection(cp.property, cp.categValues)),
			filtering = new Filtering(filters, noDeprecated.isDefined, contPatterns.map(_.property)),
			sort = sortBy,
			offset = offset.fold(0)(_.offset)
		)

		val varNames: Map[Property[_], String] = (categPatterns ++ contPatterns).map(p => p.property -> p.propVarName).toMap

		val fetchExpr = new DataObjectFetchNode(fetch, varNames + (DobjUri -> dobjVarName))

		val deepest = allPatterns.maxBy(p => p.expressions.map(nodeDepth).max)
		val deepestExpr = deepest.expressions.maxBy(nodeDepth)
		deepest.expressions.filter(_ ne deepestExpr).foreach(_.replaceWith(new SingletonSet))
		deepestExpr.replaceWith(fetchExpr)

		val patternsToRemove = allPatterns.filter{

			//filenames are not stored in the index, so falling back with their fetching to the standard SPARQL
			case cpp: ContPropPattern if cpp.property == FileName => false

			//the deepest expression is used to place the new QueryModelNode in its stead
			case `deepest` => false

			case _ => true
		}

		patternsToRemove.foreach(_.removeExpressions())
	}

}
