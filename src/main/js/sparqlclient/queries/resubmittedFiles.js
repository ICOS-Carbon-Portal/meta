export const resubmittedFiles = `prefix prov: <http://www.w3.org/ns/prov#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select ?rdfGraph ?fileName ?dobj ?submEnd where{
	graph ?rdfGraph {
		?dobj cpmeta:hasName ?fileName .
		?dobj2 cpmeta:hasName ?fileName .
		filter(?dobj != ?dobj2)
		?dobj cpmeta:wasSubmittedBy [prov:endedAtTime ?submEnd] .
		?dobj2 cpmeta:wasSubmittedBy [prov:endedAtTime ?submEnd2] .
		filter not exists{?dobj2 cpmeta:isNextVersionOf ?dobj}
		filter not exists{?dobj cpmeta:isNextVersionOf ?dobj2}
	}
}
order by ?rdfGraph ?fileName ?submEnd`;
