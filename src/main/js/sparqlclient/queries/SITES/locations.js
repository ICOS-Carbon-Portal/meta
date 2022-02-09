export const locations = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
SELECT ?stationName ?name ?json
WHERE {
	?station cpmeta:operatesOn ?site .
	?station cpmeta:hasName ?stationName .
	?site cpmeta:hasSpatialCoverage ?cov .
	?cov cpmeta:asGeoJSON ?json .
	?cov rdfs:label ?name }
order by ?station ?name`