package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.utils.AnyRefWithSafeOptTypecast

import org.eclipse.rdf4j.query.algebra.{Filter, ValueExpr}
import org.eclipse.rdf4j.query.algebra.{And, Or}
import org.eclipse.rdf4j.query.algebra.Compare
import org.eclipse.rdf4j.query.algebra.Var
import org.eclipse.rdf4j.query.algebra.ValueConstant
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap._
import se.lu.nateko.cp.meta.services.sparql.index.{Filter => IndexFilter, And => IndexAnd, Or => IndexOr, _}
import scala.util.Try
import java.time.Instant

class FilterPatternSearch(varInfo: String => Option[ContProp]){
	import FilterPatternSearch._

	def parseFilterExpr(expr: ValueExpr): Option[IndexFilter] = expr match {
		case and: And => for(
			left <- parseFilterExpr(and.getLeftArg);
			right <- parseFilterExpr(and.getRightArg)
		) yield IndexAnd(Seq(left, right))

		case or: Or => for(
			left <- parseFilterExpr(or.getLeftArg);
			right <- parseFilterExpr(or.getRightArg)
		) yield IndexOr(Seq(left, right))

		case cmp: Compare =>
			(cmp.getLeftArg, cmp.getRightArg) match{
				case (v: Var, c: ValueConstant) => getContFilter(v, c, cmp.getOperator)
				case (c: ValueConstant, v: Var) => getContFilter(v, c, swapOp(cmp.getOperator))
				case _ => None
			}

		case _ => None
	}

	//TODO Generalize to support categ filters in the filter exprs
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

			reqOpt.map(ContFilter(prop, _))
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

	def parsePropValueFilter(prop: Property, v: Value): Option[IndexFilter] = prop match{
		case uriProp: UriProperty with CategProp =>
			v.asOptInstanceOf[IRI].map(iri => CategFilter(uriProp, Seq(iri)))

		case optUriProp: OptUriProperty =>
			v.asOptInstanceOf[IRI].map(iri => CategFilter(optUriProp, Seq(Some(iri))))

		case dp: DateProperty =>
			v.asOptInstanceOf[Literal].flatMap(asTsEpochMillis).map(d => ContFilter(dp, EqualsFilter(d)))

		case FileName =>
			v.asOptInstanceOf[Literal].flatMap(asString).map(fn => ContFilter(FileName, EqualsFilter(fn)))

		case FileSize =>
			v.asOptInstanceOf[Literal].flatMap(asLong).map(fs => ContFilter(FileSize, EqualsFilter(fs)))

		case SamplingHeight =>
			v.asOptInstanceOf[Literal].flatMap(asFloat).map(sh => ContFilter(SamplingHeight, EqualsFilter(sh)))

	}


}
