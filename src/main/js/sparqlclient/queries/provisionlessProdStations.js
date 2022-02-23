export const provisionlessProdStations = `PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
SELECT ?s ?Name WHERE {
	?stClass rdfs:subClassOf cpmeta:IcosStation .
	?s a ?stClass .
	filter not exists {
		?provS cpst:hasProductionCounterpart ?prodS
		filter (str(?prodS) = str(?s))
	}
	?s cpmeta:hasName ?Name .
}
order by ?s`;