package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._

import se.lu.nateko.cp.meta.services.CpmetaVocab


class DataObjectFetchPatternSearch(meta: CpmetaVocab){
	import PatternFinder._

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
	val dataObjSpecPatternSearch: TopNodeSearch[ObjSpecPattern] = takeNode
		.ifIs[StatementPattern]
		.thenSearch(sp => {
			val (s, p, o) = splitTriple(sp)
			if(meta.hasObjectSpec == p.getValue && !s.isAnonymous && !o.isAnonymous)
				Some(new ObjSpecPattern(sp, s.getName, o.getName))
			else
				None
		})
		.recursive

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {
		val tempCoverage = new TempCoveragePatternSearch(meta)

		val noDeprecatedOpt = isLatestDobjVersionFilter(node)
		val specOpt = dataObjSpecPatternSearch(node)
		val dataStartOpt = tempCoverage.startTimePattern(node)
		val dataEndOpt = tempCoverage.endTimePattern(node)

		val res = new DataObjectFetchPattern(specOpt, noDeprecatedOpt, dataStartOpt, dataEndOpt)
		val dobjVarNameVersions = res.allPatterns.map(_.dobjVar).distinct

		if(dobjVarNameVersions.length == 1) Some(res) else None
	}
}
