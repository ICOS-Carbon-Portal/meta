package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._

import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.model.IRI
import DataObjectFetchPattern._
import PatternFinder._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.{Filter => _, _}
import StatementPatternSearch._

class DataObjectFetchPatternSearch(meta: CpmetaVocab){
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

	val dataObjSpecPatternSearch: CategPatternSearch = StatementPatternSearch
		.byPredicate(meta.hasObjectSpec)
		.filter(sp => !sp.getSubjectVar.isAnonymous)
		.recursive
		.thenFlatMap{sp =>
			val objVar = sp.getObjectVar
			if(objVar.hasValue) {
				//?dobj cpmeta:hasObjectSpec <iri>
				_ => objVar.getValue match{
					case iri: IRI => Some(Nil -> Seq(iri))
					case _ => None
				}
			} else if(!objVar.isAnonymous) {
				// ?dobj cpmeta:hasObjectSpec ?spec . VALUES ?spec {<iri1> ... <irin>} (VALUES is optional)
				BindingSetAssignmentSearch.byVarName(objVar.getName).recursive
					.optional//allow unbound ?spec
					.thenGet(
						_.fold[(Seq[TupleExpr], Seq[Spec.ValueType])](
							Nil -> Nil
						)(
							bsas => Seq(bsas.expr) -> bsas.values
						)
					)
			} else _ => None
		}
		.thenGet{
			case (sp, (exprs, vals)) =>
				val objVar = sp.getObjectVar
				val propVarName = if(objVar.isAnonymous) None else Some(objVar.getName)
				sp.getSubjectVar.getName -> categPattern(exprs :+ sp, Spec, propVarName)(vals)
		}

	val stationPatternSearch: CategPatternSearch = twoStepPropPath(meta.wasAcquiredBy, meta.prov.wasAssociatedWith)
		.thenFlatMap{tspp =>
			val statVar = tspp.step2.getObjectVar
			if(statVar.hasValue) {
				//?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith <iri>
				_ => statVar.getValue match{
					case iri: IRI => Some(Nil -> Seq(Some(iri)))
					case _ => None
				}
			} else if(!statVar.isAnonymous) {
				// ?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station . VALUES ?station {<iri1> ... <irin>}
				BindingSetAssignmentSearch.byVarName(tspp.objVariable).recursive
					.optional//allow unbound ?station
					.thenGet(
						_.fold[(Seq[TupleExpr], Seq[Station.ValueType])](
							Nil -> Nil
						)(
							bsas => Seq(bsas.expr) -> bsas.values.map(Some(_))
						)
					)
			} else _ => None

		}
		.thenGet{
			case (tspp, (bsasExprs, vals)) =>
				val exprs = Seq(tspp.step1, tspp.step2) ++ bsasExprs
				val objVar = tspp.step2.getObjectVar
				val propVarName = if(objVar.isAnonymous) None else Some(objVar.getName)
				tspp.subjVariable -> categPattern(exprs, Station, propVarName)(vals)
		}

	// ?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter . VALUES ?submitter {<iri1> ... <irin>}
	val submitterPatternSearch: CategPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.wasAssociatedWith)
		.filter(!_.step2.getObjectVar.isAnonymous)
		.thenFlatMap{tspp =>
			BindingSetAssignmentSearch.byVarName(tspp.objVariable).recursive
		}
		.thenGet{
			case (tspp, bsas) =>
				val exprs = Seq(tspp.step1, tspp.step2, bsas.expr)
				tspp.subjVariable -> categPattern(exprs, Submitter, Some(tspp.objVariable))(bsas.values)
		}

	//	?dobj cpmeta:wasSubmittedBy/prov:startedAtTime ?submTime .
	val submStartPatternSearch: ContPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.startedAtTime)
		.thenGet(tsppResult(SubmissionStart))

	//	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	val submEndPatternSearch: ContPatternSearch = twoStepPropPath(meta.wasSubmittedBy, meta.prov.endedAtTime)
		.thenGet(tsppResult(SubmissionEnd))

	val tempCoverage = new TempCoveragePatternSearch(meta)

	// ?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart
	val dataStartSearch: ContPatternSearch = tempCoverage.startTimePattern
		.thenGet{tcp =>
			tcp.dobjVar -> new ContPropPattern(Seq(tcp.expr), DataStart, tcp.timeVar)
		}
		.orElse{
			// ?dobj cpmeta:wasAcquiredBy / prov:startedAtTime ?timeStart
			twoStepPropPath(meta.wasAcquiredBy, meta.prov.startedAtTime)
				.thenGet(tsppResult(DataStart))
		}

	// ?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd
	val dataEndSearch: ContPatternSearch = tempCoverage.endTimePattern
		.thenGet{tcp =>
			tcp.dobjVar -> new ContPropPattern(Seq(tcp.expr), DataEnd, tcp.timeVar)
		}
		.orElse{
			// ?dobj cpmeta:wasAcquiredBy / prov:endedAtTime ?timeEnd
			twoStepPropPath(meta.wasAcquiredBy, meta.prov.endedAtTime)
				.thenGet(tsppResult(DataEnd))
		}

	private def simpleContPattSearch(pred: IRI, prop: ContProp): ContPatternSearch =
		StatementPatternSearch.byPredicate(pred)
			.thenSearch(nonAnonymous)
			.thenGet(sp => sp.subjVar -> new ContPropPattern(Seq(sp.sp), prop, sp.objVar))
			.recursive

	val fileNameSearch = simpleContPattSearch(meta.hasName, FileName)
	val fileSizeSearch = simpleContPattSearch(meta.hasSizeInBytes, FileSize)

	val offsetSearch: TopNodeSearch[OffsetPattern] = takeNode
		.ifIs[Slice]
		.thenGet(new OffsetPattern(_))
		.filter(_.offset > 0)

	def continuousVarPatterns(node: QueryModelNode): Seq[ContResult] = submStartPatternSearch(node).toSeq ++ submEndPatternSearch(node) ++
		dataStartSearch(node) ++ dataEndSearch(node) ++
		fileNameSearch(node) ++ fileSizeSearch(node)

	def filterSearch(contPatts: Seq[ContPropPattern]): FilterPatternSearch = {
		val contPropLookup: Map[String, ContProp] = contPatts.map(cpp => cpp.propVarName -> cpp.property).toMap
		new FilterPatternSearch(contPropLookup.get)
	}

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {

		val noDeprecatedOpt = isLatestDobjVersionFilter(node)

		val categPatts = dataObjSpecPatternSearch(node).toSeq ++ stationPatternSearch(node) ++ submitterPatternSearch(node)

		val contPatts = continuousVarPatterns(node)
		val filterSearcher = filterSearch(contPatts.map(_._2))

		val dobjVarNames = categPatts.map(_._1) ++ contPatts.map(_._1) ++ noDeprecatedOpt.map(_.dobjVar)
		//detecting the most common variable name for data obj uri (for example, could be '?dobj')
		val dobjVarNameOpt = dobjVarNames.groupBy(identity).mapValues(_.size).toSeq.sortBy(_._2).lastOption.map(_._1)

		dobjVarNameOpt
			.map{dobjVar =>
				new DataObjectFetchPattern(
					dobjVar,
					categPatts.collect{ case (`dobjVar`, cp) => cp },
					contPatts.collect { case (`dobjVar`, cp) => cp },
					filterSearcher.search(node),
					noDeprecatedOpt,
					OrderPatternSearch.search.recursive(node),
					offsetSearch(node)
				)
			}
			.filter{res =>
				val patternsToBeInSameQuery = res.allPatterns.filter{
					case _: OffsetPattern => false
					case _ => true
				}

				val dobjSpecificPatternsCount = res.allPatterns.count{
					//filename prop occurs on many other types of resources, not just data objs
					case cont: ContPropPattern if cont.property == FileName => false
					case _: PropPattern => true
					case _ => false
				}

				dobjSpecificPatternsCount >= 1 && areWithinSameQuery(
					patternsToBeInSameQuery.flatMap(_.expressions)
				)
			}
	}
}

object DataObjectFetchPatternSearch{
	type DobjVarName = String
	type CategResult = (DobjVarName, CategPropPattern)
	type ContResult = (DobjVarName, ContPropPattern)
	type CategPatternSearch = TopNodeSearch[CategResult]
	type ContPatternSearch = TopNodeSearch[ContResult]

	def tsppResult(prop: ContProp)(tspp: TwoStepPropPath): ContResult = {
		//the first statement pattern is left hanging, to be optimized away if not needed anywhere else
		val exprs = Seq(tspp.step2)
		tspp.subjVariable -> new ContPropPattern(exprs, prop, tspp.objVariable)
	}

}
