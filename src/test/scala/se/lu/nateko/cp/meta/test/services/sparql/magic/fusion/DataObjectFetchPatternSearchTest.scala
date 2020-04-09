package se.lu.nateko.cp.meta.test.services.sparql.magic.fusion

import org.scalatest.funspec.AnyFunSpec
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.algebra.TupleExpr

import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.fusion._
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.IntervalFilter
import se.lu.nateko.cp.meta.services.sparql.index._
import PatternFinder._

class DataObjectFetchPatternSearchTests extends AnyFunSpec{
	private val meta = new CpmetaVocab(new MemValueFactory)
	private val dofps = new DataObjectFetchPatternSearch(meta)
	private val parser = new SPARQLParser

	private def parseQuery(q: String): TupleExpr = parser.parseQuery(q, "http://dummy.org").getTupleExpr

	describe("Optimizing data object list fetching query"){

		def getFetchNode(queryStr: String): (TupleExpr, DataObjectFetchNode) = {
			val query = parseQuery(queryStr)
			val patternOpt = dofps.search(query)

			val pattern = patternOpt.getOrElse(fail("Pattern was not found!"))
			pattern.fuse()

			val fetchOpt = takeNode.ifIs[DataObjectFetchNode].recursive(query)
			query -> fetchOpt.getOrElse(fail("DataObjectFetch expression did not appear in the query!"))
		}

		describe("Portal app's filtered data objects query optimization"){

			lazy val (query @ _, fetchNode) = getFetchNode(TestQueries.fetchDobjListFromNewIndex)

			// it("Query AST is simplified after fusion"){
			// 	QueryOptimizer.optimize(query)
			// 	println(query.toString)
			// }

			it("correctly finds data object and object spec variable names"){
				val varNames = fetchNode.varNames.values.toIndexedSeq
				assert(varNames.contains("dobj"))
				assert(varNames.contains("spec"))
			}

			it("correctly finds temporal coverage variable names"){
				val varNames = fetchNode.varNames.values.toIndexedSeq
				assert(varNames.contains("timeStart"))
				assert(varNames.contains("timeEnd"))
			}

			it("identifies the no-deprecated-objects filter"){
				assert(fetchNode.fetchRequest.filter.exists{
					case FilterDeprecated =>
				})
			}

			it("detects and fuses the station property path pattern"){
				val varNames = fetchNode.varNames.values.toIndexedSeq
				assert(varNames.contains("station"))
			}

			it("detects and fuses the submitter property path pattern"){
				val varNames = fetchNode.varNames.values.toIndexedSeq
				assert(varNames.contains("submitter"))
			}

			it("detects order-by clause"){
				val sortBy = fetchNode.fetchRequest.sort.get
				assert(sortBy.property == SubmissionEnd)
				assert(sortBy.descending)
			}

			it("detects offset clause"){
				assert(fetchNode.fetchRequest.offset == 20)
			}

			it("detects the filters"){
				val filters = fetchNode.fetchRequest.filter.optimize.collect{
					case cf @ ContFilter(_, _) => cf
				}
				assert(filters.size === 3)
				val props = filters.map(_.property).toSet
				assert(props === Set(DataStart, DataEnd, SubmissionEnd))
				assert(filters.exists{
					case ContFilter(prop, IntervalFilter(_, _)) if prop == SubmissionEnd => true
					case _ => false
				})
			}

			it("there is no early dobj initialization in the query after fusion"){
				assert(!EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}
		}

		describe("Jupyter search for co2/co/ch4 mixing ratio data objects"){
			lazy val (query @ _, fetchNode) = getFetchNode(TestQueries.unknownSpec)
			lazy val req = fetchNode.fetchRequest

			// it("Query AST is simplified after fusion"){
			// 	QueryOptimizer.optimize(query)
			// 	println(query.toString)
			// }

			it("Only spec selection is present (non-bound)"){
				val selections = req.filter.collect{
					case c @ CategFilter(_, _) => c
				}
				assert(selections.length == 1, "")
				val sel = selections.head
				assert(sel.category == Spec)
				assert(sel.values.isEmpty)
			}

			it("No continuous-prop filters are present, sorting is left untouched"){
				assert(req.filter.exists{case ContFilter(_, _) => } === false)
				assert(req.sort.isEmpty)
				assert(query.toString.contains("OrderElem"))
			}

			it("Deprecated objects are filtered out"){
				assert(req.filter.exists{case FilterDeprecated => })
			}

			it("Expected variables are detected and dealt with"){
				val actualVars = fetchNode.varNames.values.toSet
				val expectedVars = Set("dobj", "spec", "timeStart", "timeEnd", "fileSize", "fileName", "height")
				assert(actualVars === expectedVars)
			}

			it("there is no early dobj initialization in the query after fusion"){
				assert(!EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}

			it("Sampling height is optimized"){
				assert(fetchNode.varNames(SamplingHeight) === "height")
			}

			it("File name is optimized"){
				assert(fetchNode.varNames(FileName) === "fileName")
			}

			it("wasAcquiredBy-pattern is left in the query"){
				val search = StatementPatternSearch.byPredicate(meta.wasAcquiredBy)
					.filter(_.getSubjectVar.getName == "dobj")
					.filter(_.getPredicateVar.isAnonymous)
				assert(search.recursive(query).isDefined)
			}
		}

		describe("ETC's latest pid/fileName search query"){
			lazy val (query @ _, fetchNode @ _) = getFetchNode(TestQueries.etcsLatest)
			lazy val req = fetchNode.fetchRequest

			// it("Query AST is simplified after fusion"){
			// 	println(parseQuery(TestQueries.etcsLatest))
			// 	println(QueryOptimizer.optimize(query))
			// }

			it("finds two selections (spec and station)"){
				assert(req.filter.collect{case CategFilter(_, _) => }.size == 2)
			}

			it("spec selection is unbound"){
				val specValues: Seq[Seq[AnyRef]] = req.filter.collect{
					case CategFilter(prop, values) if prop == Spec => values
				}
				assert(specValues.nonEmpty)
				assert(specValues.flatten.isEmpty)
			}

			it("station is bound to single constant"){
				val stationValueSeqs: Seq[Seq[AnyRef]] = req.filter.collect{
					case CategFilter(prop, values) if prop == Station => values
				}
				val stationValues = stationValueSeqs.flatten
				assert(stationValues.length == 1)
				assert(stationValues.head.asInstanceOf[Option[IRI]].get.stringValue == "http://meta.icos-cp.eu/resources/stations/ES_DE-HoH")
			}

			it("filtering is present"){
				assert(req.filter.collect{case ContFilter(_, _) => }.length == 1)
			}

			it("Sorting is performed but left untouched in the query, to be done again on the whole result"){
				assert(req.sort.isDefined)
				assert(query.toString.contains("OrderElem"))
			}

			it("there is no early dobj initialization in the query after fusion"){
				assert(!EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}
		}

		describe("Simple select by spec and station (constants)"){
			lazy val (query @ _, fetchNode @ _) = getFetchNode(TestQueries.simpleSpecStationSelect)
			lazy val req = fetchNode.fetchRequest

			it("finds two selections (spec and station)"){
				assert(req.filter.collect{case CategFilter(_, _) => }.size == 2)
			}

			it("spec selection is bound to a single constant"){
				val specValueSeqs: Seq[Seq[AnyRef]] = req.filter.collect{
					case CategFilter(prop, values) if prop == Spec => values
				}
				val specValues = specValueSeqs.flatten
				assert(specValues.length == 1)
				assert(specValues.head.asInstanceOf[IRI].stringValue == "http://meta.icos-cp.eu/resources/cpmeta/etcEddyFluxRawSeriesCsv")
			}

			it("station is bound to single constant"){
				val stationValueSeqs: Seq[Seq[AnyRef]] = req.filter.collect{
					case CategFilter(prop, values) if prop == Station => values
				}
				val stationValues = stationValueSeqs.flatten
				assert(stationValues.length == 1)
				assert(stationValues.head.asInstanceOf[Option[IRI]].get.stringValue == "http://meta.icos-cp.eu/resources/stations/ES_DE-HoH")
			}

			it("there is no early dobj initialization in the query after fusion"){
				assert(!EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}
		}

		describe("Last 100 uploaded objects, filtered by date using OR-expression"){
			lazy val (query @ _, fetchNode @ _) = getFetchNode(TestQueries.last100uploadedFilteredByDateWithOR)

			it("detects the OR-expression"){
				assert(fetchNode.fetchRequest.filter.optimize.exists{
					case Or(Seq(ContFilter(prop1, _), ContFilter(prop2, _))) if prop1 == SubmissionEnd && prop2 == SubmissionEnd =>
				})
			}
			ignore("Query AST is simplified after fusion"){
//				println(parseQuery(TestQueries.last100uploaded))
				println("After fusion:")
				println(query)
				println("After optimization:")
				println(QueryOptimizer.optimize(query))
			}
		}

		describe("EarlyDobjInitSearch"){

			it("Found in previous-versions query"){
				val (query,_) = getFetchNode(TestQueries.prevVersions)
				assert(EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}

			it("Found in storage infos query"){
				val (query,_) = getFetchNode(TestQueries.storageInfos)
				assert(EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}
		}
	}

	describe("BindingSetAssignmentSearch"){
		val query = parseQuery(TestQueries.fetchDobjListFromNewIndex)

		it("finds station inline values in the query"){
			val bsas = BindingSetAssignmentSearch.byVarName("station").recursive(query).get
			assert(bsas.values.length == 2)
		}
	}

	describe("two-step property path detection"){
		it("acquisition-start in: ?dobj cpmeta:wasAcquiredBy [prov:startedAtTime ?timeStart ; ... ]"){
			val query = parseQuery(TestQueries.unknownSpec)
			val resOpt = StatementPatternSearch.twoStepPropPath(meta.wasAcquiredBy, meta.prov.startedAtTime)(query)
			assert(resOpt.isDefined)
		}
	}

}

