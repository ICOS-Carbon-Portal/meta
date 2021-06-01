
export const stationsTable = `PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
SELECT
(coalesce(?tcName, ?hoName) as ?Name)
?Theme
(coalesce(?tcClass, ?hoClass) as ?Class)
(coalesce(?tcCountry, ?hoCountry) as ?Country)
FROM <http://meta.icos-cp.eu/resources/stationentry/>
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
FROM <http://meta.icos-cp.eu/resources/icos/>
WHERE {
	?s cpst:hasCountry ?hoCountry ; cpst:hasLongName ?hoName ; cpst:hasStationClass ?hoClass0 ; a/rdfs:label ?Theme .
	optional{
		?s cpst:hasProductionCounterpart ?prods .
		bind(iri(?prods) as ?prod) .
		optional{?prod cpmeta:hasName ?tcName }
		optional{?prod cpmeta:hasStationClass ?tcClass}
		optional{?prod cpmeta:countryCode ?tcCountry}
	}
	bind(if(contains(?hoClass0, 'Ass'), 'Associated', ?hoClass0) as ?hoClass)
	filter exists {?s cpst:hasShortName []}
}
order by ?Theme ?Name`;
