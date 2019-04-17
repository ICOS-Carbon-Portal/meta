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

	val isLatestDobjVersionFilter: TopNodeSearch[ExcludeDeprecatedPattern] = takeNode
		.ifIs[Filter]
		.thenAlsoSearch(
			notExistsSingleStatementPattern.thenSearch(deprecatedDobjVarName)
		)
		.thenGet{
			case (filter, varName) => new ExcludeDeprecatedPattern(filter, varName)
		}
		.recursive

	def dataObjFetchPatternSearch(filterOpt: Option[ExcludeDeprecatedPattern]): TopNodeSearch[DataObjectFetchPattern] = takeNode
		.ifIs[StatementPattern]
		.thenSearch(sp => {
			val (s, p, o) = splitTriple(sp)
			val dobjVarIsSame = filterOpt.forall(_.dobjVarName == s.getName)
			if(meta.hasObjectSpec == p.getValue && !s.isAnonymous && !o.isAnonymous && dobjVarIsSame)
				Some(new DataObjectFetchPattern(sp, filterOpt.map(_.filter)))
			else
				None
		})
		.recursive

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {
		val filterOpt = isLatestDobjVersionFilter(node)
		val startNode = filterOpt.fold(node)(_.filter)
		dataObjFetchPatternSearch(filterOpt)(startNode)
	}
}

class ExcludeDeprecatedPattern(val filter: Filter, val dobjVarName: String)

class DataObjectFetchPattern(val objSpec: StatementPattern, val noDeprecated: Option[Filter]){
	def fuse(): Unit = {
		noDeprecated.foreach{filter =>
			filter.replaceWith(filter.getArg)
		}
		objSpec.replaceWith(new DataObjectFetch(objSpec, noDeprecated.isDefined))
	}
}
