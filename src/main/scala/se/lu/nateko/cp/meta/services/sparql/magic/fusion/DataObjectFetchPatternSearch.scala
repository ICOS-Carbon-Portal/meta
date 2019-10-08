package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._

import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.model.IRI


class DataObjectFetchPatternSearch(meta: CpmetaVocab){
	import PatternFinder._
	import StatementPatternSearch._

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

	// ?dobj cpmeta:hasObjectSpec ?spec
	val dataObjSpecPatternSearch: TopNodeSearch[NamedVarPattern] = byPredicate(meta.hasObjectSpec)
		.thenSearch(nonAnonymous)
		.recursive

	// def twoStepPropPatternSearch(pred1: IRI, pred2: IRI): TopNodeSearch[TwoStepPropPathPattern] =
	// 	StatementPatternSearch.twoStepPropPath(pred1, pred2).thenGet(new TwoStepPropPathPattern(_))

	val stationPatternSearch = twoStepPropPath(meta.wasAcquiredBy, meta.prov.wasAssociatedWith)
	val submStartPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.startedAtTime)
	val submEndPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.endedAtTime)

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {
		val tempCoverage = new TempCoveragePatternSearch(meta)

		val noDeprecatedOpt = isLatestDobjVersionFilter(node)
		val specOpt = dataObjSpecPatternSearch(node)
		val dataStartOpt = tempCoverage.startTimePattern(node)
		val dataEndOpt = tempCoverage.endTimePattern(node)
		val submStartOpt = submStartPatternSearch(node)
		val submEndOpt = submEndPatternSearch(node)
		val stationOpt = stationPatternSearch(node)

		specOpt.flatMap{spec =>
			val res = new DataObjectFetchPattern(noDeprecatedOpt)
			val dobjVarNameVersions = res.allPatterns.map(_.dobjVar).distinct
			val inSameJoin = areWithinCommonJoin(res.allPatterns.flatMap(_.exprs))
	
			if(inSameJoin && dobjVarNameVersions.length == 1 && res.allPatterns.length > 1) Some(res) else None
		}
	}
}
