export const samplingPoints = `PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
SELECT ?stationName ?location ?samplingPoint ?latitude ?longitude
WHERE {
	?station cpmeta:operatesOn ?site .
	?station cpmeta:hasName ?stationName .
	?site cpmeta:hasSpatialCoverage ?cov .
	?site cpmeta:hasSamplingPoint ?point .
	?point cpmeta:hasLatitude ?latitude .
	?point cpmeta:hasLongitude ?longitude .
	?point rdfs:label ?samplingPoint .
	?cov rdfs:label ?location }
order by ?station ?location ?samplingPoint`