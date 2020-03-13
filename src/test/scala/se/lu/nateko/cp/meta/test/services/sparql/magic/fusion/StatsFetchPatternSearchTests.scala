package se.lu.nateko.cp.meta.test.services.sparql.magic.fusion
import org.scalatest.funspec.AnyFunSpec
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch
import org.eclipse.rdf4j.query.algebra.TupleExpr
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch.GroupPattern

class StatsFetchPatternSearchTests extends AnyFunSpec{
	private val sfps = new StatsFetchPatternSearch(new CpmetaVocab(new MemValueFactory))

	private def parseQuery(q: String) = (new SPARQLParser).parseQuery(q, "http://dummy.org").getTupleExpr

	private def query = parseQuery(StatsFetchPatternSearchTests.query)

	// it("prints query"){
	// 	val q = query
	// 	val patt = sfps.search(q).get
	// 	patt.fuse()
	// 	println(q)
	// }

	it("detects the GROUP BY clause without the site pattern/variable"){
		val groupOpt = sfps.groupSearch("dobj")(query)
		groupOpt match{
			case Some(GroupPattern(filtering, "submitter", "stationOpt", "spec", None)) =>
				assert(filtering.filterDeprecated)
				assert(filtering.requiredProps.size == 1)
				assert(filtering.filters.size == 1)

			case _ => fail("group pattern was not detected")
		}
	}

	it("detects the GROUP BY clause with the site pattern/variable"){
		val withSite = parseQuery(StatsFetchPatternSearchTests.queryWithSite)
		val groupOpt = sfps.groupSearch("dobj")(withSite)
		groupOpt match{
			case Some(GroupPattern(filtering, "submitter", "station", "spec", Some("site"))) =>
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
	select (count(?dobj) as ?count) ?spec ?submitter
	(if(bound(?stationOpt), ?stationOpt, <https://dummy.unbound.station>) as ?station0)
	where{
		?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
		?dobj cpmeta:hasObjectSpec ?spec .
		OPTIONAL{?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?stationOpt }
		?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
		FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj}
		FILTER (?timeStart >= '2019-01-01T00:00:00.000Z'^^xsd:dateTime)
	}
	group by ?spec ?submitter ?stationOpt
	"""

	val queryWithSite = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select (count(?dobj) as ?count) ?spec ?submitter ?station ?site
	where{
		?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
		?dobj cpmeta:hasObjectSpec ?spec .
		OPTIONAL{?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station }
		OPTIONAL{?dobj cpmeta:wasAcquiredBy/cpmeta:wasPerformedAt ?site }
		?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
		FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj}
		FILTER (?timeStart >= '2019-01-01T00:00:00.000Z'^^xsd:dateTime)
	}
	group by ?spec ?submitter ?station ?site
	"""

	val nestingQuery = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select ?spec ?submitter ?submitterLabel ?project ?projectLabel ?count
	(if(bound(?stationName), ?station0, ?stationName) as ?station)
	(if(bound(?stationName), CONCAT(?stPrefix, ?stationName), "(not applicable)") as ?stationLabel)
	where{
		{
			select
				(if(bound(?stationOpt), ?stationOpt, <https://dummy.unbound.station>) as ?station0)
				?submitter ?spec (count(?dobj) as ?count)
			where{
				?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
				?dobj cpmeta:hasObjectSpec ?spec .
				OPTIONAL{?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?stationOpt }
				?dobj cpmeta:hasSizeInBytes ?size .
				?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
				FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj}
				FILTER (?timeStart >= '2010-01-01T00:00:00.000Z'^^xsd:dateTime)
			}
			group by ?spec ?submitter ?stationOpt
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
