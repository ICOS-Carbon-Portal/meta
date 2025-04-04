package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.model.vocabulary.GEO
import org.eclipse.rdf4j.model.{IRI, Literal, Value}
import org.eclipse.rdf4j.query.algebra.{And, Compare, Exists, Not, Or, Regex as RdfRegex, StatementPattern, ValueConstant, ValueExpr, Var}
import org.locationtech.jts.io.WKTReader
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.*
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.utils.asOptInstanceOf
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.util.Try
import scala.util.matching.Regex

class FilterPatternSearch(varProps: Map[QVar, Property], meta: CpmetaVocab){
	import FilterPatternSearch.*

	def parseFilterExpr(expr: ValueExpr): Option[index.Filter] = expr match {
		case and: And => for(
			left <- parseFilterExpr(and.getLeftArg);
			right <- parseFilterExpr(and.getRightArg)
		) yield index.And(Seq(left, right))

		case or: Or => for(
			left <- parseFilterExpr(or.getLeftArg);
			right <- parseFilterExpr(or.getRightArg)
		) yield index.Or(Seq(left, right))

		case not: Not => parseFilterExpr(not.getArg).map(index.Not(_))

		case cmp: Compare =>
			(cmp.getLeftArg, cmp.getRightArg) match{
				case (v: Var, c: ValueConstant) => getFilter(v, c, cmp.getOperator)
				case (c: ValueConstant, v: Var) => getFilter(v, c, swapOp(cmp.getOperator))
				case _ => None
			}

		case exists: Exists => exists.getSubQuery match
			case sp: StatementPattern =>

				val (s, p, o) = splitTriple(sp)

				if(meta.isNextVersionOf == p.getValue && s.isAnonymous && !s.hasValue && isDobj(o))
					Some(index.Exists(DeprecationFlag))
				else if(!o.hasValue && isDobj(s))
					getExistsPropLookup(meta).get(p.getValue).map(index.Exists(_))
				else None

			case expr =>

				val dofPatt0 = DofPattern.Empty.copy(
					dobjVar = varProps.collectFirst{ case (qvar: NamedVar, DobjUri) => qvar}
				)
				val dofPatt = dofPatt0.join(DofPatternSearch(meta).find(expr))
				val fusions = DofPatternFusion(meta).findFusions(dofPatt)

				fusions match
					case Seq(fus: DobjListFusion) if fus.isPureCpIndexQuery => Some(fus.fetch.filter)
					case _ => None

		case r: RdfRegex => (r.getArg, r.getPatternArg) match{
			case (v: Var, c: ValueConstant) => for(
				lit <- c.getValue.asOptInstanceOf[Literal];
				prop <- varProps.get(QVar(v)).collect{case cp: CategProp => cp}
			) yield{
				val regex = new Regex(lit.stringValue)
				GeneralCategFilter[prop.ValueType](prop, v => regex.matches(v.toString))
			}
			case _ => None
		}

		case _ => None
	}

	private def isDobj(v: Var): Boolean = varProps.get(QVar(v)) == Some(DobjUri)

	private def getFilter(left: Var, right: ValueConstant, op: Compare.CompareOp): Option[index.Filter] = {
		import Compare.CompareOp.*

		def makeContFilter(prop: ContProp)(limit: prop.ValueType): Option[ContFilter[prop.ValueType]] = {

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

		if(left.isAnonymous) None else varProps.get(QVar(left)).flatMap{
			case contProp: ContProp => right.getValue match{
				case lit: Literal => contProp match{
					case dp: DateProperty => asTsEpochMillis(lit).flatMap(makeContFilter(dp))
					case FileName         => asString(lit).flatMap(makeContFilter(FileName))
					case FileSize         => asLong(lit).flatMap(makeContFilter(FileSize))
					case SamplingHeight   => asFloat(lit).flatMap(makeContFilter(SamplingHeight))
				}
				case _ => None
			}
			case prop =>
				if(op != EQ) None else parsePropValueFilter(prop, right.getValue)
		}
	}

}

object FilterPatternSearch{
	import Compare.CompareOp.*

	def swapOp(op: Compare.CompareOp): Compare.CompareOp = op match{
		case EQ => EQ
		case GE => LE
		case GT => LT
		case LE => GE
		case LT => GT
		case NE => NE
	}

	def parsePropValueFilter(prop: Property, v: Value): Option[index.Filter] = prop match{
		case uriProp: UriProperty =>
			v.asOptInstanceOf[IRI].map(iri => CategFilter(uriProp, Seq(iri)))

		case optUriProp: OptUriProperty =>
			v.asOptInstanceOf[IRI].map(iri => CategFilter(optUriProp, Seq(Some(iri))))

		case strProp: StringCategProp =>
			v.asOptInstanceOf[Literal].flatMap(asString).map(varName => CategFilter(strProp, Seq(varName)))

		case dp: DateProperty =>
			v.asOptInstanceOf[Literal].flatMap(asTsEpochMillis).map(d => ContFilter(dp, EqualsFilter(d)))

		case FileName =>
			v.asOptInstanceOf[Literal].flatMap(asString).map(fn => ContFilter(FileName, EqualsFilter(fn)))

		case FileSize =>
			v.asOptInstanceOf[Literal].flatMap(asLong).map(fs => ContFilter(FileSize, EqualsFilter(fs)))

		case SamplingHeight =>
			v.asOptInstanceOf[Literal].flatMap(asFloat).map(sh => ContFilter(SamplingHeight, EqualsFilter(sh)))

		case _: BoolProperty => None

		case GeoIntersects => v.asOptInstanceOf[Literal].flatMap: lit =>
			val dt = lit.getDatatype()
			if dt === GEO.WKT_LITERAL then
				Try(new WKTReader().read(lit.stringValue())).toOption.map: geom =>
					GeoFilter(GeoIntersects, geom)
			else None
	}


	def getExistsPropLookup(meta: CpmetaVocab): Map[Value, Property] = Map(
		meta.hasName         -> FileName,
		meta.hasSizeInBytes  -> FileSize,
		meta.hasVariableName -> HasVarList,
		meta.hasKeyword      -> Keyword
	)
}
