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

	val etcsLatest = """
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix cpres: <http://meta.icos-cp.eu/resources/cpmeta/>
	prefix esstation: <http://meta.icos-cp.eu/resources/stations/ES_>
	prefix prov: <http://www.w3.org/ns/prov#>
	select ?pid ?fileName
	where{
		{
			SELECT ?spec WHERE {
				VALUES (?ftype ?spec){
					("DHP" cpres:digHemispherPics )
					("EC" cpres:etcEddyFluxRawSeriesBin )
					("EC" cpres:etcEddyFluxRawSeriesCsv )
					("ST" cpres:etcStorageFluxRawSeriesBin )
					("ST" cpres:etcStorageFluxRawSeriesCsv )
					("BM" cpres:etcBioMeteoRawSeriesBin )
					("BM" cpres:etcBioMeteoRawSeriesCsv )
					("SAHEAT" cpres:etcSaheatFlagFile )
					("CEP" cpres:ceptometerMeasurements )
				}
				FILTER((?ftype = "EC"))
			}
		}
		?dobj cpmeta:hasObjectSpec ?spec ;
			cpmeta:wasAcquiredBy/prov:wasAssociatedWith esstation:DE-HoH ;
			cpmeta:wasAcquiredBy/prov:startedAtTime ?acqStartTime ;
			cpmeta:wasSubmittedBy/prov:startedAtTime ?submStartTime .
		FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj }
		BIND(substr(str(?dobj), strlen(str(?dobj)) - 23) AS ?pid)
		?dobj cpmeta:hasName ?fileName .
		FILTER(?submStartTime > "2019-10-10T21:00:00Z"^^xsd:dateTime)
	}
	order by ?acqStartTime
	"""

	val simpleSpecStationSelect = """
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix cpres: <http://meta.icos-cp.eu/resources/cpmeta/>
	prefix esstation: <http://meta.icos-cp.eu/resources/stations/ES_>
	prefix prov: <http://www.w3.org/ns/prov#>
	select ?dobj where{
		?dobj cpmeta:hasObjectSpec cpres:etcEddyFluxRawSeriesCsv ;
		cpmeta:wasAcquiredBy/prov:wasAssociatedWith esstation:DE-HoH .
	}
	"""

	val last100uploaded = """
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select (str(?submTime) as ?time) ?dobj ?spec ?dataLevel ?fileName where{
		?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
		?dobj cpmeta:hasName ?fileName .
		?dobj cpmeta:hasObjectSpec [rdfs:label ?spec ; cpmeta:hasDataLevel ?dataLevel].
	}
	order by desc(?submTime)
	limit 100
	"""

	val storageInfos = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	|select * where{
	|	?dobj cpmeta:hasObjectSpec/cpmeta:hasFormat ?format .
	|	?dobj cpmeta:hasSizeInBytes ?size .
	|	?dobj cpmeta:hasName ?fileName .
	|	filter (?format != cpmeta:asciiWdcggTimeSer)
	|}""".stripMargin

	val prevVersions = """prefix prov: <http://www.w3.org/ns/prov#>
	|prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	|select distinct ?dobj where{
	|	?dobj cpmeta:hasName "$fileName" .
	|	?dobj cpmeta:wasSubmittedBy [prov:endedAtTime ?submEnd]
	|}
	|order by desc(?submEnd)
	|limit 2""".stripMargin
}
