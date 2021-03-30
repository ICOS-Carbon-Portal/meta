export const labelingStatus = `PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?stationTheme
(coalesce(?tcId, ?hoId) as ?id)
(coalesce(?tcName, ?hoName) as ?name)
(if(
	bound(?status),
	if(
		strstarts(?status, "STEP"),
		?status,
		concat("STEP1", ?status)
	),
	"AWAITINGSTEP1"
	) as ?labelingStatus)
?lastModified
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
FROM <http://meta.icos-cp.eu/resources/stationentry/>
FROM <http://meta.icos-cp.eu/resources/icos/>
FROM NAMED <http://meta.icos-cp.eu/resources/stationlabeling/>
WHERE {
	?s cpst:hasShortName ?hoId .
	?s cpst:hasLongName ?hoName .
	?s a [ rdfs:label ?stationTheme] .
	OPTIONAL{GRAPH ?g { ?s cpst:hasApplicationStatus ?status }}
	OPTIONAL{GRAPH ?g { ?s cpst:hasAppStatusDate ?lastModified }}
	OPTIONAL{
		?s cpst:hasProductionCounterpart ?psStr .
		bind(iri(?psStr) as ?ps)
		?ps cpmeta:hasStationId ?tcId .
		?ps cpmeta:hasName ?tcName .
	}
}
ORDER BY ?stationTheme ?id
`;
