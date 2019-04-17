package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.query.algebra.TupleExpr

class DataObjectFetchPatternSearchTests extends FunSpec{
	private val dofps = new DataObjectFetchPatternSearch(new CpmetaVocab(new MemValueFactory))
	private val parser = new SPARQLParser

	private def parseQuery(q: String): TupleExpr = parser.parseQuery(q, "http://dummy.org").getTupleExpr

	describe("Optimizing data object list fetching query"){
		val query = parseQuery(TestQs.fetchDobjList)

		describe("Locating the pattern"){
			val patternOpt = dofps.search(query)

			assert(patternOpt.isDefined)

			val pattern = patternOpt.get

			it("correctly finds the variable names"){
				val (s, _, o) = splitTriple(pattern.objSpec)
				assert(s.getName == "dobj" && o.getName == "spec")
			}

			it("identifies the no-deprecated-objects filter"){
				assert(pattern.noDeprecated.isDefined)
			}

			it("can successfully fuse the pattern"){
				pattern.fuse()
				println(query)
				//pattern.noDeprecated.map(_.getParentNode) foreach println
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