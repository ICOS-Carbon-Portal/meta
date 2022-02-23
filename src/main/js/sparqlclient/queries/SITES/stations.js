export const stations = `PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX sitesmeta: <https://meta.fieldsites.se/ontologies/sites/>
SELECT * WHERE {
	?station a sitesmeta:Station ; cpmeta:hasName ?name .
}
order by ?name`;
