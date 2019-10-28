package se.lu.nateko.cp.meta.test.services.sparql.magic.fusion
import org.scalatest.FunSpec
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch
import org.eclipse.rdf4j.query.algebra.TupleExpr
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch.GroupPattern

class StatsFetchPatternSearchTests extends FunSpec{
	private val sfps = new StatsFetchPatternSearch(new CpmetaVocab(new MemValueFactory))

	private def parseQuery(q: String) = (new SPARQLParser).parseQuery(q, "http://dummy.org").getTupleExpr

	private def query = parseQuery(StatsFetchPatternSearchTests.query)

	// it("prints query"){
	// 	println(query)
	// }

	it("detects the GROUP BY clause"){
		val groupOpt = sfps.groupSearch("dobj")(query)
		groupOpt match{
			case Some(GroupPattern(filtering, "submitter", "station", "spec")) =>
				assert(filtering.filterDeprecated)
				assert(filtering.requiredProps.size == 1)
				assert(filtering.filters.size == 1)

			case _ => fail("group pattern was not detected")
		}
	}

	it("detects the stats-fetch pattern"){
		val statOpt = sfps.search(query)
		assert(statOpt.isDefined)
	}

	it("works with nesting queries"){
		val nesting = parseQuery(StatsFetchPatternSearchTests.nestingQuery)
		assert(sfps.search(nesting).isDefined)
	}
}

object StatsFetchPatternSearchTests{
	val query = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select (count(?dobj) as ?count) ?spec ?station ?submitter where{
		?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
		?dobj cpmeta:hasObjectSpec ?spec .
		?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
		?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
		FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj}
		FILTER (?timeStart >= '2019-01-01T00:00:00.000Z'^^xsd:dateTime)
	}
	group by ?spec ?submitter ?station
	"""

	val nestingQuery = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select ?spec ?submitter ?submitterLabel ?project ?projectLabel ?count
	(if(bound(?stationName), ?station0, ?stationName) as ?station)
	(if(bound(?stationName), CONCAT(?stPrefix, ?stationName), "(not applicable)") as ?stationLabel)
	where{
		{
			select ?station0 ?submitter ?spec (count(?dobj) as ?count) where{
				?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
				?dobj cpmeta:hasObjectSpec ?spec .
				?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station0 .
				?dobj cpmeta:hasSizeInBytes ?size .
				?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
				FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj}
				FILTER (?timeStart >= '2010-01-01T00:00:00.000Z'^^xsd:dateTime)
			}
			group by ?spec ?submitter ?station0
		}
		OPTIONAL{?station0 cpmeta:hasName ?stationName}
		OPTIONAL{?station0 cpmeta:hasStationId ?stId}
		BIND( IF(bound(?stId), CONCAT("(", ?stId, ") "),"") AS ?stPrefix)
		FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
		?spec cpmeta:hasAssociatedProject ?project .
		FILTER NOT EXISTS {?project cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
		?submitter cpmeta:hasName ?submitterLabel .
		?project rdfs:label ?projectLabel .
	}
	"""
}
