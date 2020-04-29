export const bySamplingHeights = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select ?dobj ?spec ?fileName ?submTime ?timeStart ?timeEnd ?samplingHeight
where {
	VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject>}
	?dobj cpmeta:hasObjectSpec ?spec .
	?dobj cpmeta:hasName ?fileName .
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	?dobj cpmeta:wasAcquiredBy [
		prov:startedAtTime ?timeStart ;
		prov:endedAtTime ?timeEnd ;
		prov:wasAssociatedWith ?station ;
		cpmeta:hasSamplingHeight ?samplingHeight
	]
	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
	FILTER( ?samplingHeight = "341"^^xsd:float || ?samplingHeight = "30"^^xsd:float)
	FILTER (?station = <http://meta.icos-cp.eu/resources/stations/AS_GAT>)
	#FILTER (?dobj = <https://meta.icos-cp.eu/objects/x2gL6BMqkQEbh928i1roE3ky> || ?dobj = <https://meta.icos-cp.eu/objects/ewvoJTTMsnEke2h9qvtnufa6>)
}
order by ?fileName
limit 100
`;
