export const dupL2s = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
prefix xsd: <http://www.w3.org/2001/XMLSchema#>
select (count (?dobj) as ?c) ?spec ?station ?samplingHeight where{
	{
		select ?dobj ?spec ?station ?samplingHeight
		where {
			{
				?spec cpmeta:hasDataLevel ?dlevel .
				filter(?dlevel = 2)
			}
			?dobj cpmeta:hasObjectSpec ?spec .
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?dobj cpmeta:hasSizeInBytes ?size .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasAcquiredBy / cpmeta:hasSamplingHeight ?samplingHeight .
		}
	}
} group by ?spec ?station ?samplingHeight
having (?c > 1)
order by ?station ?spec ?samplingHeight
`;