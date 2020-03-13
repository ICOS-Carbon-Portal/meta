package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.jdk.CollectionConverters._
import se.lu.nateko.cp.meta.services.CpmetaVocab
import PatternFinder._
import org.eclipse.rdf4j.query.algebra.Group
import org.eclipse.rdf4j.query.algebra.Count
import org.eclipse.rdf4j.query.algebra.Var
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.Filtering
import org.eclipse.rdf4j.query.algebra.Extension
import org.eclipse.rdf4j.query.algebra.ValueExpr
import org.eclipse.rdf4j.query.algebra.LeftJoin

class StatsFetchPatternSearch(meta: CpmetaVocab){
	import StatsFetchPatternSearch._
	import StatementPatternSearch._

	private val dofps = new DataObjectFetchPatternSearch(meta)

	val search: TopNodeSearch[StatsFetchPattern] = takeNode
		.ifIs[Extension]
		.thenSearch{ext =>
			for(
				(countVar, dobjVar) <- singleCountExtension(ext);
				grPatt <- groupSearch(dobjVar)(ext)
			) yield new StatsFetchPattern(
				ext,
				new StatsFetchNode(countVar, grPatt)
			)
		}
		.recursive

	def groupSearch(dobjVar: String): TopNodeSearch[GroupPattern] = takeNode
		.ifIs[Group]
		.recursive
		.thenSearch{g =>
			for(
				countVar <- singleVarCountGroup(g) if countVar == dobjVar;
				submitterVar <- agentSearch(countVar, meta.wasSubmittedBy)(g);
				stationVar <- stationSearch(countVar)(g);
				siteVarOpt <- siteSearch(countVar)(g);
				specVar <- specSearch(countVar)(g)
				if g.getGroupBindingNames.asScala == Set(specVar, submitterVar, stationVar) ++ siteVarOpt;
				filtering = filterSearch(countVar, g)
			) yield new GroupPattern(filtering, submitterVar, stationVar, specVar, siteVarOpt)
		}

	def agentSearch(dobjVar: String, provPred: IRI): TopNodeSearch[String] = provSearch(dobjVar, provPred, meta.prov.wasAssociatedWith)

	def provSearch(dobjVar: String, provPred: IRI, objPred: IRI): TopNodeSearch[String] = twoStepPropPath(provPred, objPred)
		.filter(_.subjVariable == dobjVar)
		.thenGet(_.objVariable)

	def stationSearch(dobjVar: String): TopNodeSearch[String] = acqSearch(dobjVar, meta.prov.wasAssociatedWith)
	def siteSearch(dobjVar: String): TopNodeSearch[Option[String]] = acqSearch(dobjVar, meta.wasPerformedAt).optional

	def acqSearch(dobjVar: String, objPred: IRI): TopNodeSearch[String] = takeNode
		.ifIs[LeftJoin]
		.thenGet(_.getRightArg)
		.thenSearch(provSearch(dobjVar, meta.wasAcquiredBy, objPred))
		.recursive

	def specSearch(dobjVar: String): TopNodeSearch[String] = byPredicate(meta.hasObjectSpec)
		.recursive
		.thenSearch(nonAnonymous)
		.filter(_.subjVar == dobjVar)
		.thenGet(_.objVar)

	def noDeprecationSearch(dobjVar: String): TopNodeSearch[Unit] = dofps.isLatestDobjVersionFilter
		.filter(_.dobjVar == dobjVar)
		.thenGet(_ => ())

	def filterSearch(dobjVar: String, node: Group): Filtering = {
		val noDepr = noDeprecationSearch(dobjVar)(node).isDefined
		val contPatts = dofps.continuousVarPatterns(node).collect{
			case (`dobjVar`, patt) => patt
		}
		val filters = dofps.filterSearch(contPatts).search(node).map(_.filters).getOrElse(Nil)
		new Filtering(filters, noDepr, contPatts.map(_.property))
	}
}

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

	case class GroupPattern(filtering: Filtering, submitterVar: String, stationVar: String, specVar: String, siteVar: Option[String])

	class StatsFetchPattern(expr: Extension, statsNode: StatsFetchNode){
		def fuse(): Unit = {
			expr.getArg.replaceWith(statsNode)
			expr.getElements.removeIf(elem => singleVarCount(elem.getExpr).isDefined)
		}
	}
}
