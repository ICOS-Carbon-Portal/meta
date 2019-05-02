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
		def getQuery = parseQuery(TestQs.fetchDobjList)

		describe("Locating the pattern"){

			val fetch: DataObjectFetch = {
				val query = getQuery
				val patternOpt = dofps.search(query)

				val pattern = patternOpt.getOrElse(fail("Pattern was not found!"))
				pattern.fuse()

				val fetchOpt = takeNode.ifIs[DataObjectFetch].recursive(query)
				fetchOpt.getOrElse(fail("DataObjectFetch expression did not appear in the query!"))
			}

			it("correctly finds data object and object spec variable names"){
				assert(fetch.dobjVar == "dobj" && fetch.specVar == Some("spec"))
			}

			it("correctly finds temporal coverage variable names"){
				assert(fetch.dataStartTimeVar == Some("timeStart") && fetch.dataEndTimeVar == Some("timeEnd"))
			}

			it("identifies the no-deprecated-objects filter"){
				assert(fetch.excludeDeprecated)
			}

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
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		}
		offset 0 limit 61
	"""
}