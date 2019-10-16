package se.lu.nateko.cp.meta.services.sparql.magic.fusion

object TestQueries{
	val fetchDobjList = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		where {
			?spec cpmeta:hasDataLevel [] .
			FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
			FILTER NOT EXISTS {
				?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean
			}
			?dobj cpmeta:hasObjectSpec ?spec .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		}
		offset 0 limit 61
	"""

	val fetchDobjListFromNewIndex = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		FROM <http://meta.icos-cp.eu/resources/atmcsv/>
		FROM <http://meta.icos-cp.eu/resources/atmprodcsv/>
		where {
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/atcPicarroL0DataObject> <http://meta.icos-cp.eu/resources/cpmeta/atcCoL2DataObject>}
			VALUES ?submitter {<http://meta.icos-cp.eu/resources/organizations/ATC>}
			VALUES ?station {<http://meta.icos-cp.eu/resources/stations/AS_NOR> <http://meta.icos-cp.eu/resources/stations/AS_HTM>}
			?dobj cpmeta:hasObjectSpec ?spec .
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
			FILTER (
				?timeStart >= '2019-01-01T00:00:00.000Z'^^xsd:dateTime && ?timeEnd  <= '2019-10-18T00:00:00.000Z'^^xsd:dateTime &&
				?submTime  >= '2018-09-03T00:00:00.000Z'^^xsd:dateTime && ?submTime <= '2019-10-10T00:00:00.000Z'^^xsd:dateTime
			)
		}
		order by desc(?submTime)
		offset 20 limit 61
	"""

	val unknownSpec = """
	prefix cpres: <http://meta.icos-cp.eu/resources/cpmeta/>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?fileName ?variable ?stationName ?height ?timeStart ?timeEnd #?stationId
		where{
			values ?vtype { cpres:co2MixingRatio cpres:coMixingRatioPpb cpres:ch4MixingRatioPpb}
			?vtype rdfs:label ?variable .
			?col cpmeta:hasValueType ?vtype .
			?dset cpmeta:hasColumn ?col .
			?spec cpmeta:containsDataset ?dset .
			?spec cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos> .
			?spec cpmeta:hasDataLevel "1"^^xsd:integer .
			?dobj cpmeta:hasObjectSpec ?spec .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:hasSizeInBytes ?fileSize .
			filter not exists {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasAcquiredBy [
				prov:startedAtTime ?timeStart ;
				prov:endedAtTime ?timeEnd ;
				prov:wasAssociatedWith/cpmeta:hasName ?stationName ;
				cpmeta:hasSamplingHeight ?height
			]
		}
		order by ?variable ?stationName ?height
	"""
}
