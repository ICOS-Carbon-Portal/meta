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
(coalesce(?tcClass, if(strstarts(?hoClass, 'Ass'), 'Associated', ?hoClass)) as ?stationClass)
(coalesce(?tcCountry, ?hoCountry) as ?country)
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
FROM <http://meta.icos-cp.eu/resources/stationentry/>
FROM <http://meta.icos-cp.eu/resources/icos/>
FROM NAMED <http://meta.icos-cp.eu/resources/stationlabeling/>
WHERE {
	?s cpst:hasShortName ?hoId ; cpst:hasLongName ?hoName .
	?s cpst:hasCountry ?hoCountry ; cpst:hasStationClass ?hoClass .
	?s a [ rdfs:label ?stationTheme] .
	OPTIONAL{GRAPH ?g { ?s cpst:hasApplicationStatus ?status }}
	OPTIONAL{GRAPH ?g { ?s cpst:hasAppStatusDate ?lastModified }}
	OPTIONAL{
		?s cpst:hasProductionCounterpart ?psStr .
		bind(iri(?psStr) as ?ps)
		?ps cpmeta:hasStationId ?tcId ; cpmeta:hasName ?tcName .
		?ps cpmeta:hasStationClass ?tcClass ; cpmeta:countryCode ?tcCountry .
	}
}
ORDER BY ?stationTheme ?id
`;
