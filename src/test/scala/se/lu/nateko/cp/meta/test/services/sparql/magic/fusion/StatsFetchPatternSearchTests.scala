package se.lu.nateko.cp.meta.test.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.parser.sparql.SPARQLParser
import org.eclipse.rdf4j.sail.memory.model.MemValueFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchPatternSearch.GroupPattern
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.{DobjStatFusion, DofPatternFusion, DofPatternSearch, StatsFetchNode}

class StatsFetchPatternSearchTests extends AnyFunSpec{
	private val meta = new CpmetaVocab(new MemValueFactory)
	private val dofps = new DofPatternSearch(meta)
	private val fuser = new DofPatternFusion(meta)

	private def parseQuery(q: String) = (new SPARQLParser).parseQuery(q, "http://dummy.org").getTupleExpr

	private def query = parseQuery(StatsFetchPatternSearchTests.query)

	private def groupSearch(query: TupleExpr): Option[StatsFetchNode] = {
		fuser.findFusions(dofps.find(query)).collectFirst{
			case dsf: DobjStatFusion => dsf.node
		}
	}

	it("applies a filter when station is required, allowing easy joining of extra station properties"){
		val qStr = StatsFetchPatternSearchTests.nestedAndWithExtraStationInfoInTheOuter
		val nodeOpt = groupSearch(parseQuery(qStr))
		assert(nodeOpt.isDefined)

		val statFilter = nodeOpt.get.group.filter

		val stationExistsFilterExists = statFilter.exists{case Exists(Station) =>}
		assert(stationExistsFilterExists)
	}

	it("detects the GROUP BY clause without the site pattern/variable"){
		val groupOpt = groupSearch(query).map(_.group)
		groupOpt match{
			case Some(GroupPattern(filter, "submitter", "stationOpt", "spec", None)) =>
				assert(filter.exists{case Not(Exists(DeprecationFlag)) =>})
				assert(requiredProps(filter) == 0)
				assert(contFilterNum(filter) == 1)

			case _ => fail("group pattern was not detected")
		}
	}

	it("detects the GROUP BY clause with the site pattern/variable"){
		val withSite = parseQuery(StatsFetchPatternSearchTests.queryWithSite)
		val groupOpt = groupSearch(withSite).map(_.group)
		groupOpt match{
			case Some(GroupPattern(filter, "submitter", "station", "spec", Some("site"))) =>
				assert(filter.exists{case Not(Exists(DeprecationFlag)) =>})
				assert(requiredProps(filter) == 0)
				assert(contFilterNum(filter) == 1)

			case _ => fail("group pattern was not detected")
		}
	}

	it("detects the stats-fetch pattern"){
		val statOpt = groupSearch(query)
		assert(statOpt.isDefined)
	}

	it("works with nesting queries"){
		val nesting = parseQuery(StatsFetchPatternSearchTests.nestingQuery)
		assert(groupSearch(nesting).isDefined)
	}

	def requiredProps(f: Filter): Int = f.optimize.collect{case Exists(prop: ContProp) => prop}.size
	def contFilterNum(f: Filter): Int = f.collect{case ContFilter(_, _) =>}.size
}

object StatsFetchPatternSearchTests{
	val query = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select (count(?dobj) as ?count) ?spec ?submitter ?stationOpt
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
			select ?stationOpt ?submitter ?spec (count(?dobj) as ?count) where{
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
		BIND (coalesce(?stationOpt, <https://dummy.unbound.station>) as ?station0)
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

	val nestedAndWithExtraStationInfoInTheOuter = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select ?station
	where{
		{
			select ?station ?site ?submitter ?spec (count(?dobj) as ?count) where{
				?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
				?dobj cpmeta:hasObjectSpec ?spec .
				?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
				OPTIONAL {?dobj cpmeta:wasAcquiredBy/cpmeta:wasPerformedAt ?site }
				?dobj cpmeta:hasSizeInBytes ?size .
			}
			group by ?spec ?submitter ?station ?site
		}
		FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
		?station cpmeta:hasName ?name
	}"""
}
