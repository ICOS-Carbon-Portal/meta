package se.lu.nateko.cp.meta.test.services.sparql.magic.fusion

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.fusion._
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.algebra.TupleExpr

import PatternFinder._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch._
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.IntervalFilter
import org.eclipse.rdf4j.model.IRI

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
				assert(fetchNode.fetchRequest.filtering.filterDeprecated)
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
				val filters = fetchNode.fetchRequest.filtering.filters
				val props = filters.map(_.property).distinct.toSet
				assert(props === Set(DataStart, DataEnd, SubmissionEnd))
				assert(filters.size === 3)
				assert(filters.find{_.property == SubmissionEnd}.get.condition.isInstanceOf[IntervalFilter[_]])
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
				assert(req.selections.length == 1, "")
				val sel = req.selections.head
				assert(sel.category == Spec)
				assert(sel.values.isEmpty)
			}

			it("No continuous-prop filters are present, sorting is left untouched"){
				assert(req.filtering.filters.isEmpty)
				assert(req.sort.isEmpty)
				assert(query.toString.contains("OrderElem"))
			}

			it("Deprecated objects are filtered out"){
				assert(req.filtering.filterDeprecated)
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
				assert(req.selections.size == 2)
			}

			it("spec selection is unbound"){
				val specValues: Seq[Seq[Any]] = req.selections.collect{
					case Selection(Spec, values) => values
				}
				assert(specValues.nonEmpty)
				assert(specValues.flatten.isEmpty)
			}

			it("station is bound to single constant"){
				val stationValueSeqs: Seq[Seq[Any]] = req.selections.collect{
					case Selection(Station, values) => values
				}
				val stationValues = stationValueSeqs.flatten
				assert(stationValues.length == 1)
				assert(stationValues.head.asInstanceOf[Option[IRI]].get.stringValue == "http://meta.icos-cp.eu/resources/stations/ES_DE-HoH")
			}

			it("filtering is present"){
				assert(req.filtering.filters.length == 1)
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
				assert(req.selections.size == 2)
			}

			it("spec selection is bound to a single constant"){
				val specValueSeqs: Seq[Seq[Any]] = req.selections.collect{
					case Selection(Spec, values) => values
				}
				val specValues = specValueSeqs.flatten
				assert(specValues.length == 1)
				assert(specValues.head.asInstanceOf[IRI].stringValue == "http://meta.icos-cp.eu/resources/cpmeta/etcEddyFluxRawSeriesCsv")
			}

			it("station is bound to single constant"){
				val stationValueSeqs: Seq[Seq[Any]] = req.selections.collect{
					case Selection(Station, values) => values
				}
				val stationValues = stationValueSeqs.flatten
				assert(stationValues.length == 1)
				assert(stationValues.head.asInstanceOf[Option[IRI]].get.stringValue == "http://meta.icos-cp.eu/resources/stations/ES_DE-HoH")
			}

			it("there is no early dobj initialization in the query after fusion"){
				assert(!EarlyDobjInitSearch.hasEarlyDobjInit(query))
			}
		}

		ignore("Last 100 uploaded objects"){
			lazy val (query @ _, fetchNode @ _) = getFetchNode(TestQueries.last100uploaded)
//			lazy val req = fetchNode.fetchRequest

			it("Query AST is simplified after fusion"){
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

