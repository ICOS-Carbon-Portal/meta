export const lastDataObjects = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>

select (str(?submTime) as ?time) ?dobj ?spec ?dataLevel ?fileName where{
	?dobj cpmeta:hasObjectSpec ?specUri .
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	?dobj cpmeta:hasName ?fileName .
	?specUri rdfs:label ?spec ; cpmeta:hasDataLevel ?dataLevel .
}
order by desc(?submTime)
limit 1000`;
