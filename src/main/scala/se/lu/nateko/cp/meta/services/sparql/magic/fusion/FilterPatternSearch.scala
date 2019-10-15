package se.lu.nateko.cp.meta.services.sparql.magic.fusion
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.ContProp
import se.lu.nateko.cp.meta.utils.rdf4j._

import PatternFinder._

import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.Filter

import org.eclipse.rdf4j.query.algebra.{Filter => FilterQMN, ValueExpr}
import org.eclipse.rdf4j.query.algebra.And
import org.eclipse.rdf4j.query.algebra.Compare
import org.eclipse.rdf4j.query.algebra.Var
import org.eclipse.rdf4j.query.algebra.ValueConstant
import org.eclipse.rdf4j.model.Literal
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.FileName
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import scala.util.Try
import java.time.Instant
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch._

//TODO Add collapsing of two Min/Max filters into one IntervalFilter
class FilterPatternSearch(varInfo: String => Option[ContProp[_]]){
	import FilterPatternSearch._
	import DataObjectFetchPattern.FilterPattern

	val search: TopNodeSearch[FilterPattern] = takeNode
		.ifIs[FilterQMN]
		.thenAlsoSearch{fqmn =>
			parseFilterConjunction(fqmn.getCondition)
		}
		.thenGet{
			case (fqmn, filters) => new FilterPattern(fqmn, filters)
		}
		.recursive


	def parseFilterConjunction(expr: ValueExpr): Option[Seq[Filter]] = expr match {
		case and: And => for(
			left <- parseFilterConjunction(and.getLeftArg);
			right <- parseFilterConjunction(and.getRightArg)
		) yield left ++ right

		case cmp: Compare =>
			(cmp.getLeftArg, cmp.getRightArg) match{
				case (v: Var, c: ValueConstant) => getFilter(v, c, cmp.getOperator).map(Seq(_))
				case (c: ValueConstant, v: Var) => getFilter(v, c, swapOp(cmp.getOperator)).map(Seq(_))
				case _ => None
			}

		case _ => None
	}

	private def getFilter(left: Var, right: ValueConstant, op: Compare.CompareOp): Option[Filter] = {

		def makeFilter[T](prop: ContProp[T])(limit: T): Option[Filter] = {
			import Compare.CompareOp._
			val reqOpt: Option[FilterRequest[T]] = op match{
				case EQ => Some(EqualsFilter(limit))
				case GT => Some(MinFilter(limit, false))
				case LT => Some(MaxFilter(limit, false))
				case GE => Some(MinFilter(limit, true))
				case LE => Some(MaxFilter(limit, true))
				case _ => None
			}
			reqOpt.map{req =>
				DataObjectFetch.filter(prop, req)
			}
		}

		val propOpt: Option[ContProp[_]] = if(!left.isAnonymous) varInfo(left.getName) else None

		val litOpt: Option[Literal] = right.getValue match{
			case lit: Literal => Some(lit)
			case _ => None
		}

		val optopt = for(prop <- propOpt; lit <- litOpt) yield prop match{

			case fn @ FileName => asString(lit).flatMap(makeFilter(fn))

			case fs @ FileSize => asLong(lit).flatMap(makeFilter(fs))

			case tsProp @ (DataEnd | DataStart | SubmissionStart | SubmissionEnd) =>
				asTsEpochMillis(lit).flatMap(makeFilter(tsProp))
		}
		optopt.flatten
	}

}

object FilterPatternSearch{
	import Compare.CompareOp._

	def swapOp(op: Compare.CompareOp): Compare.CompareOp = op match{
		case EQ => EQ
		case GE => LE
		case GT => LT
		case LE => GE
		case LT => GT
		case NE => NE
	}

	def asString(lit: Literal): Option[String] = if(lit.getDatatype === XMLSchema.STRING) Some(lit.stringValue) else None

	def asLong(lit: Literal): Option[Long] = if(lit.getDatatype === XMLSchema.LONG) Try(lit.stringValue.toLong).toOption else None

	def asTsEpochMillis(lit: Literal): Option[Long] = if(lit.getDatatype === XMLSchema.DATETIME)
		Try(Instant.parse(lit.stringValue).toEpochMilli).toOption
	else None

	// def collapseIntervals(filters: Seq[Filter]): Seq[Filter] = {
	// 	val (minMaxes, rest) = filters.partition(f => f.condition match{
	// 		case MaxFilter(_, _) => true
	// 		case MinFilter(_, _) => true
	// 		case _ => false
	// 	})
	// 	if(minMaxes.isEmpty) filters else{
	// 		val collapsed = minMaxes.groupBy(_.property).flatMap{case (prop, propFilters) => {

	// 			def makeFilter[T](prop: ContProp[T], cond1: FilterRequest[T], cond2: FilterRequest[T]): Seq[Filter] = (cond1, cond2) match{
	// 				case (min: MinFilter[T], max: MaxFilter[T]) =>
	// 					Seq(DataObjectFetch.filter[T](prop, IntervalFilter(min, max)))
	// 				case (min: MinFilter[T], max: MaxFilter[T]) =>
	// 					Seq(DataObjectFetch.filter[T](prop, IntervalFilter(min, max)))
	// 			}


	// 			if(propFilters.size != 2) propFilters else propFilters match{
	// 				case Seq(Filter(p, cond1), Filter(`p`, cond2)) => makeFilter(p, cond1, cond2)
	// 				case _ => Nil
	// 			}
	// 		}}
	// 		???
	// 		//collapsed +: rest
	// 	}
	// }
}
