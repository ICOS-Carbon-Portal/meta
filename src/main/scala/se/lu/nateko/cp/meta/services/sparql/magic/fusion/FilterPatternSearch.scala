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
class FilterPatternSearch(varInfo: String => Option[ContProp]){
	import FilterPatternSearch._
	import DataObjectFetchPattern.FilterPattern

	val search: TopNodeSearch[FilterPattern] = takeNode
		.ifIs[FilterQMN]
		.thenAlsoSearch{fqmn =>
			parseFilterConjunction(fqmn.getCondition).map(collapseIntervals)
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

		def makeFilter[T](prop: ContProp{type ValueType = T})(limit: T): Option[Filter] = {
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

		val propOpt: Option[ContProp] = if(!left.isAnonymous) varInfo(left.getName) else None

		val litOpt: Option[Literal] = right.getValue match{
			case lit: Literal => Some(lit)
			case _ => None
		}

		val optopt: Option[Option[Filter]] = for(prop <- propOpt; lit <- litOpt) yield prop match{
			//TODO Revisit this code segment with Scala 2.13 or later (try to remove repetition)
			case FileName        => asString(lit).flatMap(makeFilter(FileName))
			case FileSize        => asLong(lit).flatMap(makeFilter(FileSize))
			case DataEnd         => asTsEpochMillis(lit).flatMap(makeFilter(DataEnd))
			case DataStart       => asTsEpochMillis(lit).flatMap(makeFilter(DataStart))
			case SubmissionStart => asTsEpochMillis(lit).flatMap(makeFilter(SubmissionStart))
			case SubmissionEnd   => asTsEpochMillis(lit).flatMap(makeFilter(SubmissionEnd))
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

	def collapseIntervals(filters: Seq[Filter]): Seq[Filter] = {

		val (minMaxes, rest) = filters.partition(f => f.condition match{
			case MaxFilter(_, _) => true
			case MinFilter(_, _) => true
			case _ => false
		})

		if(minMaxes.isEmpty) filters else{
			val collapsed = minMaxes.groupBy(_.property).flatMap{case (prop, propFilters) => {

				type VT = prop.ValueType

				def makeFilter(cond1: FilterRequest[VT], cond2: FilterRequest[VT]): Option[IntervalFilter[VT]] = (cond1, cond2) match{
					case (min: MinFilter[VT], max: MaxFilter[VT]) => Some(IntervalFilter(min, max))
					case (max: MaxFilter[VT], min: MinFilter[VT]) => Some(IntervalFilter(min, max))
					case _ => None
				}

				propFilters.toList match{
					case Filter(`prop`, cond1) :: Filter(`prop`, cond2) :: Nil =>
						//TODO Revisit the next line in next versions of Scala (2.13+), the casts should not be needed
						makeFilter(cond1.asInstanceOf[FilterRequest[VT]], cond2.asInstanceOf[FilterRequest[VT]])
							.map{intFilt =>
								DataObjectFetch.filter[VT](prop, intFilt)
							}
							.fold(propFilters)(Seq(_))
					case _ =>
						propFilters
				}

			}}
			collapsed.toSeq ++ rest
		}
	}
}
