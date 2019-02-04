export const lastDataObjects = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select (str(?submTime) as ?time) ?dobj ?spec ?dataLevel ?fileName where{
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	?dobj cpmeta:hasName ?fileName .
	?dobj cpmeta:hasObjectSpec [rdfs:label ?spec ; cpmeta:hasDataLevel ?dataLevel].
}
order by desc(?submTime)
limit 1000`;
