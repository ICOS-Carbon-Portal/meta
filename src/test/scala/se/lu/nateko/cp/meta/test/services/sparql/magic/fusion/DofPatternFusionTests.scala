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

class DofPatternFusionTests extends AnyFunSpec{
	private val meta = new CpmetaVocab(new MemValueFactory)
	private val dofps = new DofPatternSearch(meta)
	private val fuser = new DofPatternFusion(meta)
	private val parser = new SPARQLParser

	private def parseQuery(q: String): TupleExpr = parser.parseQuery(q, "http://dummy.org").getTupleExpr


	def getFetchNode(queryStr: String): (TupleExpr, DataObjectFetchNode) = {
		val query = parseQuery(queryStr)
		val pattern = dofps.find(query)
		val fusions = fuser.findFusions(pattern)
		DofPatternRewrite.rewrite(query, fusions)

		val fetchOpt = takeNode.ifIs[DataObjectFetchNode].recursive(query)
		query -> fetchOpt.getOrElse(fail("DataObjectFetch expression did not appear in the query!"))
	}

	describe("Portal app's filtered data objects query optimization"){

		lazy val (query @ _, fetchNode) = getFetchNode(TestQueries.fetchDobjListFromNewIndex)

		// it("Query AST is simplified after fusion"){
		// 	QueryOptimizer.optimize(query)
		// 	println(query.toString)
		// 	println(fetchNode.fetchRequest)
		// 	println(fetchNode.varNames)
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

		it("BindingSetAssingments are cleaned up from the query"){
			assert(!query.toString.contains("BindingSetAssignment"))
		}
	}

	describe("Jupyter search for co2/co/ch4 mixing ratio data objects"){
		lazy val (query @ _, fetchNode) = getFetchNode(TestQueries.unknownSpec)
		lazy val req = fetchNode.fetchRequest

		// it("Query AST is simplified after fusion"){
		// 	QueryOptimizer.optimize(query)
		// 	println(query.toString)
		// 	println(fetchNode.fetchRequest)
		// 	println(fetchNode.varNames)
		// }

		it("No category filters are present"){
			val selections = req.filter.collect{
				case c @ CategFilter(_, _) => c
			}
			assert(selections.isEmpty)
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
		// 	println(query)
		// 	println(fetchNode.varNames)
		// }

		it("finds only one categ filter (station)"){
			val categs = req.filter.collect{case CategFilter(prop, _) => prop}
			assert(categs == Seq(Station))
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

		it("Sorting is not performed and left untouched in the query, to be done on the whole result"){
			assert(req.sort.isEmpty)
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

		it("detects the sorting"){
			assert(fetchNode.fetchRequest.sort.isDefined)
		}
		// ignore("Query AST is simplified after fusion"){
		// 	println(parseQuery(TestQueries.last100uploaded))
		// 	println("After fusion:")
		// 	println(query)
		// 	println("After optimization:")
		// 	println(QueryOptimizer.optimize(query))
		// }
	}

	describe("query with submission end time being present, but not being used by filters"){
		val queryText = """
			|prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			|prefix prov: <http://www.w3.org/ns/prov#>
			|select ?dobj ?submEnd
			|where {
			|	VALUES ?spec { <http://meta.icos-cp.eu/resources/cpmeta/atcCo2NrtGrowingDataObject> }
			|	?dobj cpmeta:hasObjectSpec ?spec .
			|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			|	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd .
			|}""".stripMargin
		lazy val (query @ _, fetchNode @ _) = getFetchNode(queryText)

		it("requires SubmissionEnd property to be present"){
			val reqProps = fetchNode.fetchRequest.filter.collect{
				case RequiredProps(props) => props
			}.flatten
			assert(reqProps.contains(SubmissionEnd))
		}
	}

	describe("query with union and bind"){
		val queryText = """
			|prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			|select ?fileName where{
			|	?dobj cpmeta:hasName ?fileName .
			|	?dobj cpmeta:hasObjectSpec ?spec .
			|	{
			|		{bind (<http://meta.icos-cp.eu/resources/cpmeta/globalCarbonBudget> as ?spec)}
			|		UNION
			|		{filter (?dobj = <https://meta.icos-cp.eu/objects/7yV2z2CffZT8d65qndeQZztL>)}
			|	}
			|}""".stripMargin
		lazy val (query @ _, fetchNode @ _) = getFetchNode(queryText)

		it("is recognized as an CP-index query with union"){
			assert(fetchNode.fetchRequest.filter.isInstanceOf[Or])
		}
	}
}
