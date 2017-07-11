export const resubmittedFiles = `
prefix prov: <http://www.w3.org/ns/prov#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select distinct ?fileName ?submEnd ?dobj1 where{
  ?dobj1 cpmeta:hasName ?fileName .
  ?dobj2 cpmeta:hasName ?fileName .
  filter(?dobj1 != ?dobj2)
  ?dobj1 cpmeta:wasSubmittedBy [prov:endedAtTime ?submEnd]
}
order by ?fileName desc(?submEnd)
`;
