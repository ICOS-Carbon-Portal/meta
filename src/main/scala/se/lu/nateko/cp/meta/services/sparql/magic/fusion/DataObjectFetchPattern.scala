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
		def property: Property
		def propVarName: String
	}

	final class ContPropPattern(val expressions: Seq[TupleExpr], val property: ContProp, val propVarName: String) extends PropPattern

	sealed trait CategPropPattern extends PropPattern{
		override val property: CategProp
		def categValues: Seq[property.ValueType]
	}

	def categPattern[T <: AnyRef](exprs: Seq[TupleExpr], prop: CategProp{type ValueType = T}, propVar: String, vals: Seq[T]) = new CategPropPattern{
		val expressions = exprs
		val property = prop
		val propVarName = propVar
		val categValues = vals
	}

	final class ExcludeDeprecatedPattern(expr: Filter, val dobjVar: String) extends UnaryTupleOpSubPattern(expr)
	final class OrderPattern(expr: Order, val sortVar: String, val descending: Boolean) extends UnaryTupleOpSubPattern(expr)
	final class FilterPattern(expr: Filter, val filters: Seq[FetchFilter]) extends UnaryTupleOpSubPattern(expr)

	final class OffsetPattern(expr: Slice) extends SubPattern{
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

		val selections = categPatterns.map(cp => selection(cp.property, cp.categValues))
		val unboundedSelectionsPresent: Boolean = selections.exists(_.values.isEmpty)

		val fetch = new DataObjectFetch(
			selections = selections,
			filtering = new Filtering(filters, noDeprecated.isDefined, contPatterns.map(_.property)),
			sort = sortBy,
			offset = offset.filter(_ => !unboundedSelectionsPresent).fold(0)(_.offset)
		)

		val varNames: Map[Property, String] = (categPatterns ++ contPatterns).map(p => p.property -> p.propVarName).toMap

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

			//cannot sort or skip if providing only partial solution, need to rely on the default sorting functionality
			case _: OrderPattern | _: OffsetPattern if unboundedSelectionsPresent => false

			case _ => true
		}

		patternsToRemove.foreach(_.removeExpressions())
	}

}
