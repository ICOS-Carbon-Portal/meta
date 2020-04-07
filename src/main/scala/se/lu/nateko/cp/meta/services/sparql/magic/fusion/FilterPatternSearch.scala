package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import se.lu.nateko.cp.meta.utils.rdf4j._

import PatternFinder._

import org.eclipse.rdf4j.query.algebra.{Filter, ValueExpr}
import org.eclipse.rdf4j.query.algebra.And
import org.eclipse.rdf4j.query.algebra.Compare
import org.eclipse.rdf4j.query.algebra.Var
import org.eclipse.rdf4j.query.algebra.ValueConstant
import org.eclipse.rdf4j.model.Literal
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap._
import se.lu.nateko.cp.meta.services.sparql.index.{Filter => IndexFilter, And => IndexAnd, _}
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import scala.util.Try
import java.time.Instant

//TODO Add searching of multiple FILTER clauses
class FilterPatternSearch(varInfo: String => Option[ContProp]){
	import FilterPatternSearch._
	import DataObjectFetchPattern.FilterPattern

	val search: TopNodeSearch[FilterPattern] = takeNode
		.ifIs[Filter]
		.thenAlsoSearch{fqmn =>
			parseFilterExpr(fqmn.getCondition)
		}
		.thenGet{
			case (fqmn, filter) => new FilterPattern(fqmn, filter)
		}
		.recursive


	def parseFilterExpr(expr: ValueExpr): Option[IndexFilter] = expr match {
		case and: And => for(
			left <- parseFilterExpr(and.getLeftArg);
			right <- parseFilterExpr(and.getRightArg)
		) yield IndexAnd(Seq(left, right))

		case cmp: Compare =>
			(cmp.getLeftArg, cmp.getRightArg) match{
				case (v: Var, c: ValueConstant) => getContFilter(v, c, cmp.getOperator)
				case (c: ValueConstant, v: Var) => getContFilter(v, c, swapOp(cmp.getOperator))
				case _ => None
			}

		case _ => None
	}

	private def getContFilter(left: Var, right: ValueConstant, op: Compare.CompareOp): Option[IndexFilter] = {

		def makeFilter(prop: ContProp)(limit: prop.ValueType): Option[ContFilter[prop.ValueType]] = {
			import Compare.CompareOp._

			val reqOpt: Option[FilterRequest[prop.ValueType]] = op match{
				case EQ => Some(EqualsFilter(limit))
				case GT => Some(MinFilter(limit, false))
				case LT => Some(MaxFilter(limit, false))
				case GE => Some(MinFilter(limit, true))
				case LE => Some(MaxFilter(limit, true))
				case _ => None
			}

			reqOpt.map(ContFilter[prop.ValueType](prop, _))
		}

		for(
			prop <- if(!left.isAnonymous) varInfo(left.getName) else None;
			lit <- right.getValue match{
				case lit: Literal => Some(lit)
				case _ => None
			};
			filter <- prop match{
				case dp: DateProperty => asTsEpochMillis(lit).flatMap(makeFilter(dp))
				case FileName         => asString(lit).flatMap(makeFilter(FileName))
				case FileSize         => asLong(lit).flatMap(makeFilter(FileSize))
				case SamplingHeight   => asFloat(lit).flatMap(makeFilter(SamplingHeight))
			}
		) yield filter
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

	def asLong(lit: Literal): Option[Long] = if(lit.getDatatype === XMLSchema.LONG) Try(lit.longValue).toOption else None
	def asFloat(lit: Literal): Option[Float] = if(lit.getDatatype === XMLSchema.FLOAT) Try(lit.floatValue).toOption else None

	def asTsEpochMillis(lit: Literal): Option[Long] = if(lit.getDatatype === XMLSchema.DATETIME)
		Try(Instant.parse(lit.stringValue).toEpochMilli).toOption
	else None

}
