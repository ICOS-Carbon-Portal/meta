
export const badmEntries = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix dcterms: <http://purl.org/dc/terms/>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
prefix owl: <http://www.w3.org/2002/07/owl#>
select ?entry ?propLabel ?value ?infoDate ?submDate
from <http://meta.icos-cp.eu/resources/badm/>
from <http://meta.icos-cp.eu/ontologies/badm/>
where{
?station cpmeta:hasAncillaryEntry ?entry .
?entry dcterms:dateSubmitted ?submDate .
OPTIONAL {?entry dcterms:date ?infoDate}
{{
?entry ?prop ?dataValue .
  ?prop rdfs:subPropertyOf cpmeta:hasAncillaryDataValue .
  BIND ( ?dataValue AS ?value ) .
} UNION {
?entry ?prop ?objectValue .
  ?prop rdfs:subPropertyOf cpmeta:hasAncillaryObjectValue .
  ?objectValue rdfs:label ?value .
}}
?prop rdfs:label ?propLabel .
}
order by ?entry`;

