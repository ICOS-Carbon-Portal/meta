
export const stationsTable = `PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
SELECT
(IF(bound(?lat), str(?lat), "?") AS ?latstr)
(IF(bound(?lon), str(?lon), "?") AS ?lonstr)
(REPLACE(str(?class),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?themeShort)
(str(?country) AS ?Country)
(str(?sName) AS ?Short_name)
(str(?lName) AS ?Long_name)
(GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)
(str(?siteType) AS ?Site_type)
FROM <http://meta.icos-cp.eu/resources/stationentry/>
WHERE {
?s cpst:hasCountry ?country .
?s cpst:hasShortName ?sName .
?s cpst:hasLongName ?lName .
?s cpst:hasSiteType ?siteType .
?s cpst:hasPi ?pi .
?pi cpst:hasLastName ?piLname .
?s a ?class .
OPTIONAL{?s cpst:hasLat ?lat } .
OPTIONAL{?s cpst:hasLon ?lon } .
OPTIONAL{?s cpst:hasSpatialReference ?spatRef } .
OPTIONAL{?pi cpst:hasFirstName ?piFname } .
}
GROUP BY ?lat ?lon ?class ?country ?sName ?lName ?siteType
ORDER BY ?themeShort ?sName`;
