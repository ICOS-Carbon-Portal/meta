export const lastDataObjects = `
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select (str(?submTime) as ?time) ?dobj ?spec ?dataLevel ?fileName ?submitterName where{
  ?dobj cpmeta:hasObjectSpec [rdfs:label ?spec ; cpmeta:hasDataLevel ?dataLevel].
  ?dobj cpmeta:hasName ?fileName .
  ?dobj cpmeta:wasSubmittedBy ?submission .
  ?submission prov:endedAtTime ?submTime .
  ?submission prov:wasAssociatedWith [cpmeta:hasName ?submitterName].
}
order by desc(?submTime)
limit 1000
`;
