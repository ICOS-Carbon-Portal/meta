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

	// FILTER NOT EXISTS {[] [] []}
	val notExistsSingleStatementPattern: NodeSearch[Filter, StatementPattern] = takeNode[Filter]
		.thenGet(_.getCondition)
		.ifIs[Not]
		.thenGet(_.getArg)
		.ifIs[Exists]
		.thenGet(_.getSubQuery)
		.ifIs[StatementPattern]

	// [] cpmeta:isNextVersionOf ?dobj
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

	// ?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station . VALUES ?station {<iri1> ... <irin>}
	val stationPatternSearch: CategPatternSearch = twoStepPropPath(meta.wasAcquiredBy, meta.prov.wasAssociatedWith)
		.thenFlatMap{tspp =>
			BindingSetAssignmentSearch.byVarName(tspp.objVariable).recursive
		}
		.thenGet{
			case (tspp, bsas) =>
				val exprs = Seq(tspp.step1, tspp.step2, bsas.expr)
				tspp.subjVariable -> categPattern(exprs, Station, tspp.objVariable, bsas.values.map(Some(_)))
		}

	// ?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter . VALUES ?submitter {<iri1> ... <irin>}
	val submitterPatternSearch: CategPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.wasAssociatedWith)
		.thenFlatMap{tspp =>
			BindingSetAssignmentSearch.byVarName(tspp.objVariable).recursive
		}
		.thenGet{
			case (tspp, bsas) =>
				val exprs = Seq(tspp.step1, tspp.step2, bsas.expr)
				tspp.subjVariable -> categPattern(exprs, Submitter, tspp.objVariable, bsas.values)
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

	// ?dobj cpmeta:wasSubmittedBy/prov:startedAtTime ?submStartTime .
	val dataStartSearch: ContPatternSearch = tempCoverage.startTimePattern
		.thenGet{tcp =>
			tcp.dobjVar -> contPattern(Seq(tcp.expr), DataStart, tcp.timeVar)
		}

	// ?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEndTime .
	val dataEndSearch: ContPatternSearch = tempCoverage.endTimePattern
		.thenGet{tcp =>
			tcp.dobjVar -> contPattern(Seq(tcp.expr), DataEnd, tcp.timeVar)
		}

	def simpleContPattSearch[T](pred: IRI, prop: ContProp[T]): ContPatternSearch =
		StatementPatternSearch.byPredicate(pred)
			.thenSearch(nonAnonymous)
			.thenGet(sp => sp.subjVar -> contPattern(Seq(sp.sp), prop, sp.objVar))
			.recursive

	val fileNameSearch = simpleContPattSearch(meta.hasName, FileName)
	val fileSizeSearch = simpleContPattSearch(meta.hasSizeInBytes, FileSize)

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {

		val noDeprecatedOpt = isLatestDobjVersionFilter(node)

		val categPatts = dataObjSpecPatternSearch(node).toSeq ++ stationPatternSearch(node) ++ submitterPatternSearch(node)

		val contPatts = submStartPatternSearch(node).toSeq ++ submEndPatternSearch(node) ++
			dataStartSearch(node) ++ dataEndSearch(node) ++
			fileNameSearch(node) ++ fileSizeSearch(node)

		val dobjVarNames = categPatts.map(_._1) ++ contPatts.map(_._1) ++ noDeprecatedOpt.map(_.dobjVar)
		val dobjVarNameOpt = dobjVarNames.groupBy(identity).mapValues(_.size).toSeq.sortBy(_._2).lastOption.map(_._1)

		dobjVarNameOpt
			.map{dobjVar =>
				new DataObjectFetchPattern(
					dobjVar,
					categPatts.collect{ case (`dobjVar`, cp) => cp },
					contPatts.collect { case (`dobjVar`, cp) => cp },
					noDeprecatedOpt,
					OrderPatternSearch.search.recursive(node)
				)
			}
			.filter{res =>
				res.allPatterns.length > 1 && {
					val patternsToBeInCommonJoin = res.allPatterns.filter{
						case _: OrderPattern => false
						case _ => true
					}
					areWithinCommonJoin(patternsToBeInCommonJoin.flatMap(_.expressions))
				}
			}
	}
}

object DataObjectFetchPatternSearch{
	type CategPatternSearch = TopNodeSearch[(String, CategPropPattern)]
	type ContPatternSearch = TopNodeSearch[(String, ContPropPattern)]
}
