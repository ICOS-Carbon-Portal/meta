
export const stationsTable = `PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
SELECT
(str(?s) AS ?id)
(IF(bound(?lat), str(?lat), "?") AS ?latstr)
(IF(bound(?lon), str(?lon), "?") AS ?lonstr)
(IF(bound(?spatRef), str(?spatRef), "?") AS ?geoJson)
(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?themeShort)
(str(?country) AS ?Country)
(str(?sName) AS ?Short_name)
(str(?lName) AS ?Long_name)
(GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)
(str(?siteType) AS ?Site_type)
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
WHERE {
?s a ?class .
OPTIONAL{?s cpst:hasLat ?lat } .
OPTIONAL{?s cpst:hasLon ?lon } .
OPTIONAL{?s cpst:hasSpatialReference ?spatRef } .
?s cpst:hasCountry ?country .
?s cpst:hasShortName ?sName .
?s cpst:hasLongName ?lName .
?s cpst:hasPi ?pi .
OPTIONAL{?pi cpst:hasFirstName ?piFname } .
?pi cpst:hasLastName ?piLname .
?s cpst:hasSiteType ?siteType .
}
GROUP BY ?s ?lat ?lon ?spatRef ?locationDesc ?class ?country ?sName ?lName ?siteType`;
