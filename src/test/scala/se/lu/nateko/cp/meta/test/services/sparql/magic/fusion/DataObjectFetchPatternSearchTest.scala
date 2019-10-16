package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.algebra.TupleExpr

import PatternFinder._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.SubmissionEnd
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.DataStart
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.DataEnd
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.IntervalFilter

class DataObjectFetchPatternSearchTests extends FunSpec{
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

			lazy val (query, getFetch) = getFetchNode(TestQueries.fetchDobjListFromNewIndex)

			it("The pattern is detected"){
				QueryOptimizer.optimize(query)
				println(query.toString)
			}

			it("correctly finds data object and object spec variable names"){
				val varNames = getFetch.varNames.values.toIndexedSeq
				assert(varNames.contains("dobj"))
				assert(varNames.contains("spec"))
			}

			it("correctly finds temporal coverage variable names"){
				val varNames = getFetch.varNames.values.toIndexedSeq
				assert(varNames.contains("timeStart"))
				assert(varNames.contains("timeEnd"))
			}

			it("identifies the no-deprecated-objects filter"){
				assert(getFetch.fetchRequest.filtering.filterDeprecated)
			}

			it("detects and fuses the station property path pattern"){
				val varNames = getFetch.varNames.values.toIndexedSeq
				assert(varNames.contains("station"))
			}

			it("detects and fuses the submitter property path pattern"){
				val varNames = getFetch.varNames.values.toIndexedSeq
				assert(varNames.contains("submitter"))
			}

			it("detects order-by clause"){
				val sortBy = getFetch.fetchRequest.sort.get
				assert(sortBy.property == SubmissionEnd)
				assert(sortBy.descending)
			}

			it("detects offset clause"){
				assert(getFetch.fetchRequest.offset == 20)
			}

			it("detects the filters"){
				val filters = getFetch.fetchRequest.filtering.filters
				val props = filters.map(_.property).distinct.toSet
				assert(props === Set(DataStart, DataEnd, SubmissionEnd))
				assert(filters.size === 3)
				assert(filters.find{_.property == SubmissionEnd}.get.condition.isInstanceOf[IntervalFilter[_]])
			}
		}

		ignore("Jupyter search for co2/co/ch4 mixing ratio data objects"){
			it("Free-spec pattern is detected"){
				val (query, _) = getFetchNode(TestQueries.unknownSpec)
				QueryOptimizer.optimize(query)
				println(query.toString)
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

