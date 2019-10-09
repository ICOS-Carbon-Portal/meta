package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.algebra.TupleExpr

import PatternFinder._

class DataObjectFetchPatternSearchTests extends FunSpec{
	private val dofps = new DataObjectFetchPatternSearch(new CpmetaVocab(new MemValueFactory))
	private val parser = new SPARQLParser

	private def parseQuery(q: String): TupleExpr = parser.parseQuery(q, "http://dummy.org").getTupleExpr

	describe("Optimizing data object list fetching query"){
		def getQuery = parseQuery(TestQs.fetchDobjListFromNewIndex)

		describe("Locating the pattern"){

			def getFetch: DataObjectFetchNode = {
				val query = getQuery
				val patternOpt = dofps.search(query)

				val pattern = patternOpt.getOrElse(fail("Pattern was not found!"))
				pattern.fuse()

				val fetchOpt = takeNode.ifIs[DataObjectFetchNode].recursive(query)
				fetchOpt.getOrElse(fail("DataObjectFetch expression did not appear in the query!"))
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
				val fetch = getFetch
				assert(fetch.fetchRequest.filtering.filterDeprecated)
			}

			it("detects and fuses the station property path pattern"){
				val varNames = getFetch.varNames.values.toIndexedSeq
				assert(varNames.contains("station"))
				//assert(fetch.stationVar === Some("station"))
			}

		}
	}

	describe("BindingSetAssignmentSearch"){
		val query = parseQuery(TestQs.fetchDobjListFromNewIndex)

		describe("parses query and prints the AST"){
			println(parseQuery(TestQs.fetchDobjListFromNewIndex).toString)
		}

		it("finds station inline values in the query"){
			val bsas = BindingSetAssignmentSearch.byVarName("station").recursive(query).get
			assert(bsas.values.length == 2)
		}
	}

}

private object TestQs{
	val fetchDobjList = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		where {
			?spec cpmeta:hasDataLevel [] .
			FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
			FILTER NOT EXISTS {
				?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean
			}
			?dobj cpmeta:hasObjectSpec ?spec .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		}
		offset 0 limit 61
	"""

	val fetchDobjListFromNewIndex = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		FROM <http://meta.icos-cp.eu/resources/atmcsv/>
		FROM <http://meta.icos-cp.eu/resources/atmprodcsv/>
		where {
			VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/atcPicarroL0DataObject> <http://meta.icos-cp.eu/resources/cpmeta/atcCoL2DataObject>}
			?dobj cpmeta:hasObjectSpec ?spec .
			VALUES ?station {<http://meta.icos-cp.eu/resources/stations/AS_NOR> <http://meta.icos-cp.eu/resources/stations/AS_HTM>}
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			VALUES ?submitter {<http://meta.icos-cp.eu/resources/organizations/ATC>}
			?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		}
		offset 20 limit 61
	"""
}
