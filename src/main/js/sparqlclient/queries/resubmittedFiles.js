export const resubmittedFiles = `prefix prov: <http://www.w3.org/ns/prov#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select ?rdfGraph ?fileName ?dobj ?submEnd ?dobj2 ?submEnd2 where{
	graph ?rdfGraph {
		?dobj cpmeta:hasName ?fileName .
		?dobj2 cpmeta:hasName ?fileName .
		filter(?dobj != ?dobj2)
		filter not exists{[] cpmeta:isNextVersionOf ?dobj }
		?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd .
		?dobj2 cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd2 .
		filter (?submEnd2 > ?submEnd)
	}
}`;
