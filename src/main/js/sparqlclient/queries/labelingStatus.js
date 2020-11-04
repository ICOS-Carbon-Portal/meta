export const labelingStatus = `PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?stationTheme ?id
(if(bound(?piName), ?piName, ?hoName) as ?name)
(if(
  bound(?status),
  if(
    strstarts(?status, "STEP"),
    ?status,
    concat("STEP1", ?status)
  ),
  "AWAITINGSTEP1"
  ) as ?labelingStatus)
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
FROM <http://meta.icos-cp.eu/resources/stationentry/>
FROM NAMED <http://meta.icos-cp.eu/resources/stationlabeling/>
WHERE {
?s cpst:hasShortName ?id .
?s cpst:hasLongName ?hoName .
?s a [ rdfs:label ?stationTheme] .
OPTIONAL{GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/>{ ?s cpst:hasApplicationStatus ?status }}
OPTIONAL{GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/>{ ?s cpst:hasLongName ?piName}}
}
ORDER BY ?stationTheme ?id
`;
