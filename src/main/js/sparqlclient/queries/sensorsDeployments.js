export const sensorsDeployments = `prefix prov: <http://www.w3.org/ns/prov#>
prefix ssn: <http://www.w3.org/ns/ssn/>
prefix xsd: <http://www.w3.org/2001/XMLSchema#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>

select ?sensorSerialNum ?sensorModel ?varName ?lat ?lon ?alt ?sensorStart ?sensorStop where{
	bind(<https://meta.icos-cp.eu/objects/6sd1F6OvZAkyUcEbp_81Tj2y> as ?dobj)
	bind(<http://meta.icos-cp.eu/resources/cpmeta/SWC_n_n_n> as ?variable)
	?dobj cpmeta:wasAcquiredBy ?acq ;
		cpmeta:hasObjectSpec/cpmeta:containsDataset/cpmeta:hasColumn ?variable .
	?acq prov:wasAssociatedWith ?station ;
		prov:startedAtTime ?dobjStart ;
		prov:endedAtTime ?dobjStop .
	?depl ssn:forProperty ?variable ; cpmeta:atOrganization ?station .
	optional{?depl cpmeta:hasVariableName ?varName }
	optional{?depl cpmeta:hasLatitude ?lat}
	optional{?depl cpmeta:hasLongitude ?lon}
	optional{?depl cpmeta:hasSamplingHeight ?alt}
	optional{?depl cpmeta:hasStartTime ?sensorStart}
	optional{?depl cpmeta:hasEndTime ?sensorStop}
	bind(coalesce(?sensorStart, "1900-01-01T00:00:00Z"^^xsd:dateTime) as ?sStart)
	bind(coalesce(?sensorStop, "2099-01-01T00:00:00Z"^^xsd:dateTime) as ?sStop)
	filter(?sStart < ?dobjStop && ?sStop > ?dobjStart)
	?sensor ssn:hasDeployment ?depl ;
		cpmeta:hasModel ?sensorModel ;
		cpmeta:hasSerialNumber ?sensorSerialNum .
}
order by ?varName ?sensorStart`;
