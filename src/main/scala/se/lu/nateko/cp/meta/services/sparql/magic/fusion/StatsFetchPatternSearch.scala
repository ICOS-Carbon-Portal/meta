package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.{Count, Extension, Group, ValueExpr, Var}
import se.lu.nateko.cp.meta.services.sparql.index.Filter

import scala.jdk.CollectionConverters.*


object StatsFetchPatternSearch{

	def singleVarCountGroup(g: Group): Option[String] = g.getGroupElements().asScala.toSeq match{
		case Seq(elem) => singleVarCount(elem.getOperator)
		case _         => None
	}

	def singleCountExtension(ext: Extension): Option[(String, String)] = ext.getElements().asScala.toSeq
		.flatMap{
			elem => singleVarCount(elem.getExpr).map(elem.getName -> _)
		} match{
			case Seq(singleCountVarNames) => Some(singleCountVarNames)
			case _ => None
		}

	def singleVarCount(expr: ValueExpr): Option[String] = expr match{
		case cnt: Count =>
			cnt.getArg match {
				case v: Var if !v.isAnonymous => Some(v.getName)
				case _ => None
			}
		case _ => None
	}

	final case class GroupPattern(filter: Filter, submitterVar: String, stationVar: String, specVar: String, siteVar: Option[String])

}
