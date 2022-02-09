export const stations = `PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX sitesmeta: <https://meta.fieldsites.se/ontologies/sites/>
SELECT *
FROM <https://meta.fieldsites.se/resources/sites/>
WHERE {
	?station a sitesmeta:Station ; cpmeta:hasName ?name .
	?station a/rdfs:subClassOf* <https://meta.fieldsites.se/ontologies/sites/Station> .
}
order by ?name`;
