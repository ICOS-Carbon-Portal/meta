package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._

import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.model.IRI
import DobjPattern._
import PatternFinder._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.{Filter => _, _}

class DataObjectFetchPatternSearch(meta: CpmetaVocab){
	import StatementPatternSearch._
	import DataObjectFetchPatternSearch._

	val notExistsSingleStatementPattern: NodeSearch[Filter, StatementPattern] = takeNode[Filter]
		.thenGet(_.getCondition)
		.ifIs[Not]
		.thenGet(_.getArg)
		.ifIs[Exists]
		.thenGet(_.getSubQuery)
		.ifIs[StatementPattern]

	val deprecatedDobjVarName: NodeSearch[StatementPattern, String] = sp => {
		val (s, p, o) = splitTriple(sp)
		if(meta.isNextVersionOf == p.getValue && s.isAnonymous && !s.hasValue && !o.isAnonymous)
			Some(o.getName)
		else
			None
	}

	// FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
	val isLatestDobjVersionFilter: TopNodeSearch[ExcludeDeprecatedPattern] = takeNode
		.ifIs[Filter]
		.thenAlsoSearch(
			notExistsSingleStatementPattern.thenSearch(deprecatedDobjVarName)
		)
		.thenGet{
			case (filter, varName) => new ExcludeDeprecatedPattern(filter, varName)
		}
		.recursive

	// ?dobj cpmeta:hasObjectSpec ?spec . VALUES ?spec {<iri1> ... <irin>}
	val dataObjSpecPatternSearch: CategPatternSearch = StatementPatternSearch
		.byPredicate(meta.hasObjectSpec).thenSearch(nonAnonymous).recursive
		.thenFlatMap(nvp => BindingSetAssignmentSearch.byVarName(nvp.objVar).recursive)
		.thenGet{
			case (nvp, bsas) =>
				val exprs = Seq(nvp.sp, bsas.expr)
				nvp.subjVar -> categPattern(exprs, Spec, nvp.objVar, bsas.values)
		}

	//?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station . VALUES ?station {<iri1> ... <irin>}
	val stationPatternSearch: CategPatternSearch = twoStepPropPath(meta.wasAcquiredBy, meta.prov.wasAssociatedWith)
		.thenFlatMap{tspp =>
			BindingSetAssignmentSearch.byVarName(tspp.objVariable).recursive
		}
		.thenGet{
			case (tspp, bsas) =>
				val exprs = Seq(tspp.step1, tspp.step2, bsas.expr)
				tspp.subjVariable -> categPattern(exprs, Station, tspp.objVariable, bsas.values.map(Some(_)))
		}

	//	?dobj cpmeta:wasSubmittedBy/prov:startedAtTime ?submTime .
	val submStartPatternSearch: ContPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.startedAtTime)
		.thenGet{tspp =>
			val exprs = Seq(tspp.step1, tspp.step2)
			tspp.subjVariable -> contPattern(exprs, SubmissionStart, tspp.objVariable)
		}

	//	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	val submEndPatternSearch: ContPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.endedAtTime)
		.thenGet{tspp =>
			val exprs = Seq(tspp.step1, tspp.step2)
			tspp.subjVariable -> contPattern(exprs, SubmissionEnd, tspp.objVariable)
		}

	val tempCoverage = new TempCoveragePatternSearch(meta)

	val dataStartSearch: ContPatternSearch = tempCoverage.startTimePattern
		.thenGet{tcp =>
			tcp.dobjVar -> contPattern(Seq(tcp.expr), DataStart, tcp.timeVar)
		}

	val dataEndSearch: ContPatternSearch = tempCoverage.endTimePattern
		.thenGet{tcp =>
			tcp.dobjVar -> contPattern(Seq(tcp.expr), DataEnd, tcp.timeVar)
		}

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {

		val noDeprecatedOpt = isLatestDobjVersionFilter(node)
		val specOpt = dataObjSpecPatternSearch(node)
		val stationOpt = stationPatternSearch(node)
		val dataStartOpt = dataStartSearch(node)
		val dataEndOpt = dataEndSearch(node)
		val submStartOpt = submStartPatternSearch(node)
		val submEndOpt = submEndPatternSearch(node)

		val categPatts = (specOpt.toSeq ++ stationOpt).map(_._2)
		val contPatts = (submStartOpt.toSeq ++ submEndOpt ++ dataStartOpt ++ dataEndOpt).map(_._2)

		val res = new DataObjectFetchPattern(categPatts, contPatts, noDeprecatedOpt)
		//TODO Perform ?dobj variable sameness-control
		//val dobjVarNameVersions = res.allPatterns.map(_.dobjVar).distinct
		val inSameJoin = areWithinCommonJoin(res.allPatterns.flatMap(_.expressions))

		if(inSameJoin /*&& dobjVarNameVersions.length == 1*/ && res.allPatterns.length > 1) Some(res) else None
	}
}

object DataObjectFetchPatternSearch{
	type CategPatternSearch = TopNodeSearch[(String, CategPropPattern)]
	type ContPatternSearch = TopNodeSearch[(String, ContPropPattern)]
}
