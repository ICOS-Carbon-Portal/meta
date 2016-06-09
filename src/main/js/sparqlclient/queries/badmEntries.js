
export const badmEntries = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix dcterms: <http://purl.org/dc/terms/>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>

select ?station ?entry ?propLabel ?value ?infoDate ?submDate
from <http://meta.icos-cp.eu/resources/badm/>
from <http://meta.icos-cp.eu/ontologies/badm/>
where{
	?station cpmeta:hasAncillaryEntry ?entry .
	?entry dcterms:dateSubmitted ?submDate .
	OPTIONAL {?entry dcterms:date ?infoDate } .
	{
		{
			?entry ?prop ?value .
			?prop rdfs:subPropertyOf cpmeta:hasAncillaryDataValue .
		} UNION {
			?entry ?prop [rdfs:label ?value ] .
			?prop rdfs:subPropertyOf cpmeta:hasAncillaryObjectValue .
		}
	}
	?prop rdfs:label ?propLabel .
}
order by ?station ?entry`;

