package se.lu.nateko.cp.meta.test.services.sparql.regression

object TestQueries {
	//from portal front-end app from data project
	val dataTypeBasics = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix xsd: <http://www.w3.org/2001/XMLSchema#>
		select ?spec ?project (?spec as ?type) ?level ?dataset ?format ?theme ?temporalResolution
		where{
			?spec cpmeta:hasDataLevel ?level ; cpmeta:hasAssociatedProject ?project .
			FILTER NOT EXISTS {?project cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
			FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
			?spec cpmeta:hasDataTheme ?theme .
			OPTIONAL{
				?spec cpmeta:containsDataset ?dataset .
				OPTIONAL{?dataset cpmeta:hasTemporalResolution ?temporalResolution}
			}
			?spec cpmeta:hasFormat ?format .
		}
	"""

	//from portal front-end app from data project
	val variables = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix xsd: <http://www.w3.org/2001/XMLSchema#>
		select distinct ?spec ?variable ?varTitle ?valType ?quantityKind
		(if(bound(?unit), ?unit, "(not applicable)") as ?quantityUnit)
		where{
			{
				?spec cpmeta:containsDataset ?datasetSpec .
				FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
				FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
			}
			{
				{
					?datasetSpec cpmeta:hasColumn ?variable .
					?variable cpmeta:hasColumnTitle ?varTitle .
				} UNION {
					?datasetSpec cpmeta:hasVariable ?variable .
					?variable cpmeta:hasVariableTitle ?varTitle .
				}
			}
			FILTER NOT EXISTS {?variable cpmeta:isQualityFlagFor [] }
			?variable cpmeta:hasValueType ?valType .
			OPTIONAL{?valType cpmeta:hasUnit ?unit }
			OPTIONAL{?valType cpmeta:hasQuantityKind ?quantityKind }
		}
	"""

	//from portal front-end app from data project
	val dataObjOriginStats = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		prefix xsd: <http://www.w3.org/2001/XMLSchema#>
		select ?spec ?countryCode ?submitter ?count ?station ?ecosystem ?location ?site ?stationclass
		where{
			{
				select ?station ?site ?submitter ?spec (count(?dobj) as ?count) where{
					?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
					?dobj cpmeta:hasObjectSpec ?spec .
					OPTIONAL {?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station }
					OPTIONAL {?dobj cpmeta:wasAcquiredBy/cpmeta:wasPerformedAt ?site }
					?dobj cpmeta:hasSizeInBytes ?size .
					FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
				}
				group by ?spec ?submitter ?station ?site
			}
			FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
			FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
			BIND (COALESCE(?station, <http://dummy>) as ?boundStation)
			OPTIONAL {?boundStation cpmeta:hasEcosystemType ?ecosystem}
			OPTIONAL {?boundStation cpmeta:countryCode ?countryCode}
			OPTIONAL {?boundStation cpmeta:hasStationClass ?stClassOpt}
			BIND (IF(
				bound(?stClassOpt),
				IF(strstarts(?stClassOpt, "Ass"), "Associated", "ICOS"),
				IF(bound(?station), "Other", ?stClassOpt)
			) as ?stationclass)
		}
	"""

	//from portal front-end app from data project
	val detailedDataObjInfo = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		prefix xsd: <http://www.w3.org/2001/XMLSchema#>
		prefix dcterms: <http://purl.org/dc/terms/>
		select distinct ?dobj ?station ?stationId ?samplingHeight ?samplingPoint ?theme ?themeIcon ?title ?description ?columnNames ?site ?hasVarInfo ?dois ?biblioInfo where{
			{
				select ?dobj (min(?station0) as ?station) (sample(?stationId0) as ?stationId) (sample(?samplingHeight0) as ?samplingHeight) (sample(?samplingPoint0) as ?samplingPoint) (sample(?site0) as ?site) (group_concat(?doi ; separator="|") as ?dois) where{
					VALUES ?dobj { <https://meta.icos-cp.eu/objects/vumO9XjaAPr2BhpULbXG6y5g> <https://meta.icos-cp.eu/objects/2CEv4dQjRpuc0rPsc-4iNMnP> <https://meta.icos-cp.eu/objects/fC_heNsEGiHdsAN2yJ4qHd4S> <https://meta.icos-cp.eu/objects/IjspljaRXa0d1sq5rNFFr8we> <https://meta.icos-cp.eu/objects/XA5AbuL5On_EmKXf14fRsNJP> <https://meta.icos-cp.eu/objects/QLH8l2Q5NX0l33noXb_gzMtP> <https://meta.icos-cp.eu/objects/eDvzC6cfBacxy3HrMj7I-MxH> <https://meta.icos-cp.eu/objects/bfgU0FHcHBw_te8v4J7WSswp> <https://meta.icos-cp.eu/objects/0zqS7zvPIwmtZqIK_v-QpcBv> <https://meta.icos-cp.eu/objects/AYOC6D-PKkFBYRJZRpu3O1RN> <https://meta.icos-cp.eu/objects/rXw-CQ_QDPvoS2SBsk0i1trG> <https://meta.icos-cp.eu/objects/xhDwOJl7oG_-uyqUyh0O7okq> <https://meta.icos-cp.eu/objects/7fi4IYmSCylhbcwfMZXaC9oO> <https://meta.icos-cp.eu/objects/FcYQCbR2RXUwWmUrQWlzTv3B> <https://meta.icos-cp.eu/objects/oYkYPd9GKwANpPL5wvPLbI8i> <https://meta.icos-cp.eu/objects/jVNix8VRcZ2Szfg1ZYpFlLfF> <https://meta.icos-cp.eu/objects/324GucSMd7xHu1NLdYQDurc1> <https://meta.icos-cp.eu/objects/qCSN8z-NQUFIcIQ1u4CEqcza> <https://meta.icos-cp.eu/objects/jIfUK-CHinN4N9bbJu8V5K0K> <https://meta.icos-cp.eu/objects/0-KtZf6FB5ZooVkeiIKNQiut> }
					OPTIONAL{
						?dobj cpmeta:wasAcquiredBy ?acq.
						?acq prov:wasAssociatedWith ?stationUri .
						OPTIONAL{ ?stationUri cpmeta:hasName ?station0 }
						OPTIONAL{ ?stationUri cpmeta:hasStationId ?stationId0 }
						OPTIONAL{ ?acq cpmeta:hasSamplingHeight ?samplingHeight0 }
						OPTIONAL{ ?acq cpmeta:hasSamplingPoint/rdfs:label ?samplingPoint0 }
						OPTIONAL{ ?acq cpmeta:wasPerformedAt/cpmeta:hasSpatialCoverage/rdfs:label ?site0 }
					}
					OPTIONAL{
						?coll dcterms:hasPart ?dobj ; cpmeta:hasDoi ?doi .
						filter not exists{[] cpmeta:isNextVersionOf ?coll ; dcterms:hasPart ?dobj}
					}
				}
				group by ?dobj
			}
			OPTIONAL{
				?dobj cpmeta:hasObjectSpec/cpmeta:hasDataTheme ?themeUri .
				?themeUri rdfs:label ?theme ; cpmeta:hasIcon ?themeIcon .
			}
			OPTIONAL{ ?dobj dcterms:title ?title }
			OPTIONAL{ ?dobj dcterms:description ?description }
			OPTIONAL{ ?dobj cpmeta:hasActualColumnNames ?columnNames }
			OPTIONAL{ ?dobj cpmeta:hasActualVariable [].BIND ("true"^^xsd:boolean as ?hasVarInfo) }
			OPTIONAL{ ?dobj cpmeta:hasBiblioInfo ?biblioInfo}
		}
	"""

	//from portal front-end app from data project
	val labels = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		select ?uri ?label ?comment ?stationId ?webpage
		from <http://meta.icos-cp.eu/ontologies/cpmeta/>
		from <http://meta.icos-cp.eu/resources/cpmeta/>
		from <http://meta.icos-cp.eu/resources/icos/>
		from <http://meta.icos-cp.eu/resources/extrastations/>
		where {
			?uri a ?class .
			optional {?uri rdfs:label ?rdfsLabel }
			optional {?uri cpmeta:hasName ?name}
			bind(coalesce(?name, ?rdfsLabel) as ?label)
			filter(
				bound(?label) &&
				?class != cpmeta:Instrument && ?class != cpmeta:Membership &&
				!strstarts(str(?class), "http://www.w3.org/2002/07/owl#")
			)
			optional {?uri cpmeta:hasStationId ?stationId }
			optional {?uri rdfs:comment ?comment }
			optional {?uri rdfs:seeAlso ?webpage}
		}
	"""

	//from portal front-end app from data project
	val keywords = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix xsd: <http://www.w3.org/2001/XMLSchema#>
		select ?spec ?keywords
		from <http://meta.icos-cp.eu/resources/cpmeta/>
		where{
			?spec cpmeta:hasAssociatedProject ?proj
			{
				{?proj cpmeta:hasKeywords ?keywords }
				UNION
				{?spec cpmeta:hasKeywords ?keywords }
			}
			filter not exists {?proj cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
		}
	"""

	//from portal front-end app from data project
	val stationPositions = """
		PREFIX prov: <http://www.w3.org/ns/prov#>
		PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		SELECT ?station ?lat ?lon
		WHERE {
			?station cpmeta:hasStationId [] .
			filter not exists {?station a <https://meta.fieldsites.se/ontologies/sites/Station>}
			?station cpmeta:hasLatitude ?lat ; cpmeta:hasLongitude ?lon .
		}
	"""

	//from js-projects/commonJs/main/station.ts in static project
	//used by stations table and stations map on the icos website
	val provisionalStationMetadata = """
		PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
		SELECT *
		FROM <http://meta.icos-cp.eu/resources/stationentry/>
		WHERE {
			{
				select ?s (GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)
				where{ ?s cpst:hasPi/cpst:hasLastName ?piLname }
				group by ?s
			}
			?s a ?owlClass .
			BIND(REPLACE(str(?owlClass),"http://meta.icos-cp.eu/ontologies/stationentry/", "") AS ?themeShort)
			?s cpst:hasShortName ?Id .
			?s cpst:hasLongName ?Name .
			OPTIONAL{?s cpst:hasLat ?lat . ?s cpst:hasLon ?lon }
			OPTIONAL{?s cpst:hasSpatialReference ?geoJson }
			OPTIONAL{?s cpst:hasCountry ?Country }
			OPTIONAL{?s cpst:hasSiteType ?Site_type }
			OPTIONAL{?s cpst:hasElevationAboveSea ?Elevation_above_sea }
			OPTIONAL{?s cpst:hasStationClass ?Station_class }
		}
	"""

	//from js-projects/commonJs/main/station.ts in static project
	//used by stations table and stations map on the icos website
	val productionStationMetadata = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
		SELECT *
		FROM <http://meta.icos-cp.eu/resources/icos/>
		FROM <http://meta.icos-cp.eu/resources/cpmeta/>
		FROM <http://meta.icos-cp.eu/resources/stationentry/>
		WHERE{
			{
				select ?s ?ps (GROUP_CONCAT(?lname; separator=";") AS ?PI_names) where {
					?s cpst:hasProductionCounterpart ?psStr .
					bind(iri(?psStr) as ?ps)
					?memb cpmeta:atOrganization ?ps ; cpmeta:hasRole <http://meta.icos-cp.eu/resources/roles/PI> .
					filter not exists {?memb cpmeta:hasEndTime []}
					?pers cpmeta:hasMembership ?memb ; cpmeta:hasLastName ?lname .
				}
				group by ?s ?ps
			}
			?ps cpmeta:hasStationId ?Id ; cpmeta:hasName ?Name .
			OPTIONAL{ ?ps cpmeta:hasElevation ?Elevation_above_sea }
			OPTIONAL{ ?ps cpmeta:hasLatitude ?lat}
			OPTIONAL{ ?ps cpmeta:hasLongitude ?lon}
			OPTIONAL{ ?ps cpmeta:hasSpatialCoverage/cpmeta:asGeoJSON ?geoJson}
			OPTIONAL{ ?ps cpmeta:countryCode ?Country}
			OPTIONAL{ ?ps cpmeta:hasStationClass  ?Station_class}
			OPTIONAL{ ?ps cpmeta:hasLabelingDate ?Labeling_date}
			BIND(?ps as ?prodUri)
		}
	"""

	//from icoscp Python library
	val atmosphericCO2Level2 = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		FROM <http://meta.icos-cp.eu/resources/atmprodcsv/>
		where {
				BIND(<http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject> AS ?spec)
				?dobj cpmeta:hasObjectSpec ?spec .
				FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
				?dobj cpmeta:hasSizeInBytes ?size .
				?dobj cpmeta:hasName ?fileName .
				?dobj cpmeta:wasSubmittedBy [
				prov:endedAtTime ?submTime ;
				prov:wasAssociatedWith ?submitter
				] .
				?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
				?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		}
	"""

	//from icoscp Python library
	def objectSpec(spec: String, station: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?station ?samplingHeight
		where {
			VALUES ?spec { <http://meta.icos-cp.eu/resources/cpmeta/$spec> }
			?dobj cpmeta:hasObjectSpec ?spec .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd .
			?dobj cpmeta:wasAcquiredBy [
				prov:wasAssociatedWith ?stationIri ;
				cpmeta:hasSamplingHeight ?samplingHeight
			] .
			?stationIri cpmeta:hasName ?station .		
			FILTER(?station = "$station")
		}
		order by ?station ?samplingHeight
	"""

	//from icoscp Python library
	val collections = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix dcterms: <http://purl.org/dc/terms/>
		select * where{
		?collection a cpmeta:Collection .
		OPTIONAL{?collection cpmeta:hasDoi ?doi} .
		?collection dcterms:title ?title .
		OPTIONAL{?collection dcterms:description ?description}
		FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?collection}
		}
		order by ?title
	"""

	//from icoscp Python library
	def collectionItems(collId: String) = s"""
		select * where{ $collId <http://purl.org/dc/terms/hasPart> ?dobj}
	"""

	//from icoscp Python library
	def stationData(station: String, level: Int) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select *
		where {
			VALUES ?station {$station}
			?dobj cpmeta:hasObjectSpec ?spec .
			FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasAcquiredBy / prov:startedAtTime ?timeStart .
			?dobj cpmeta:wasAcquiredBy / prov:endedAtTime ?timeEnd .
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?spec rdfs:label ?specLabel .
			OPTIONAL {?dobj cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight ?samplingheight} .
			?spec cpmeta:hasDataLevel ?datalevel .
			?dobj cpmeta:hasSizeInBytes ?bytes .
			FILTER (?datalevel = $level)
		}
	"""

	//from icoscp Python library
	def stations(id: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select *
		from <http://meta.icos-cp.eu/resources/icos/> 
		from <http://meta.icos-cp.eu/resources/extrastations/>
		from <http://meta.icos-cp.eu/resources/cpmeta/> 
		where {
			BIND ( "$id"^^xsd:string as ?id)
			?uri cpmeta:hasStationId ?id .   
			OPTIONAL {?uri cpmeta:hasName ?name  } .
			OPTIONAL {?uri cpmeta:countryCode ?country }.
			OPTIONAL {?uri cpmeta:hasLatitude ?lat }.
			OPTIONAL {?uri cpmeta:hasLongitude ?lon }.
			OPTIONAL {?uri cpmeta:hasElevation ?elevation } .
		}
	"""

	//from icoscp Python library
	def dataObjStation(dObj: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select distinct ?dobj ?stationName ?stationId ?samplingHeight ?longitude ?latitude ?elevation ?theme
		where{
				{	select ?dobj (min(?station0) as ?stationName)
					(sample(?stationId0) as ?stationId) 
					(sample(?stationLongitude) as ?longitude)
					(sample(?stationLatitude) as ?latitude)
					(sample(?stationElevation) as ?elevation)
					(sample(?samplingHeight0) as ?samplingHeight)
					where{
						VALUES ?dobj { $dObj }
						OPTIONAL{
								?dobj cpmeta:wasAcquiredBy ?acq.
								?acq prov:wasAssociatedWith ?stationUri .
								OPTIONAL{ ?stationUri cpmeta:hasName ?station0 }
								OPTIONAL{ ?stationUri cpmeta:hasStationId ?stationId0 }
								OPTIONAL{ ?stationUri cpmeta:hasLongitude ?stationLongitude }
								OPTIONAL{ ?stationUri cpmeta:hasLatitude ?stationLatitude }
								OPTIONAL{ ?stationUri cpmeta:hasElevation ?stationElevation }
								OPTIONAL{ ?acq cpmeta:hasSamplingHeight ?samplingHeight0 }
						}
					}
					group by ?dobj
				}
				?dobj cpmeta:hasObjectSpec ?specUri .
				OPTIONAL{ ?specUri cpmeta:hasDataTheme [rdfs:label ?theme ;]}
				OPTIONAL{?dobj cpmeta:hasActualColumnNames ?columnNames }
		}
	"""

	//from icoscp Python library
	val ATCStations = """
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	select *
	from <http://meta.icos-cp.eu/resources/icos/>
	where{
		{
			select ?station (GROUP_CONCAT(?piLname; separator=";") AS ?PI_names)
			where{
				?station a cpmeta:AS .
				?piMemb cpmeta:atOrganization ?station  .
				?piMemb cpmeta:hasRole <http://meta.icos-cp.eu/resources/roles/PI> .
				filter not exists {?piMemb cpmeta:hasEndTime []}
				?pi cpmeta:hasMembership ?piMemb .
				?pi cpmeta:hasLastName ?piLname .
			}
			group by ?station
		}
		?station cpmeta:hasName ?stationName ;
			cpmeta:hasStationId ?stationId ;
			cpmeta:countryCode ?Country ;
			cpmeta:hasLatitude ?lat ;
			cpmeta:hasLongitude ?lon .
	}
	order by ?stationId
	"""

	//from icoscp Python library
	val ATCStationsLevel1Data = """
		prefix cpres: <http://meta.icos-cp.eu/resources/cpmeta/>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?fileName ?variable ?stationName ?height ?timeStart ?timeEnd #?stationId
		where{
			values ?vtype { cpres:co2MixingRatio cpres:coMixingRatioPpb cpres:ch4MixingRatioPpb}
			#values ?spec {cpres:atcCo2NrtGrowingDataObject cpres:atcCoNrtGrowingDataObject cpres:atcCh4NrtGrowingDataObject}
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
				#prov:wasAssociatedWith/cpmeta:hasStationId ?stationId ;
				prov:startedAtTime ?timeStart ;
				prov:endedAtTime ?timeEnd ;
				prov:wasAssociatedWith ?stationIri 
			] .
			?stationIri cpmeta:hasName ?stationName .
			?dobj cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight ?height .
		}
		order by ?variable ?stationName ?height
	"""

	//from icoscp Python library
	val stationClasses = """
		prefix st: <http://meta.icos-cp.eu/ontologies/stationentry/>
		select distinct ?stationId ?stationClass ?country ?longName
		from <http://meta.icos-cp.eu/resources/stationentry/>
		where{
			?s a st:AS .
			?s st:hasShortName ?stationId .
			?s st:hasStationClass ?stationClass .
			?s st:hasCountry ?country .
			?s st:hasLongName ?longName .
			filter (?stationClass = "1" || ?stationClass = "2")
		}
		ORDER BY ?stationClass ?stationId 
	"""

	//from icoscp Python library
	def ATCStationList(station: String, tracer: String, dObj: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		FROM <http://meta.icos-cp.eu/resources/atmprodcsv/>
		where {
			BIND(<http://meta.icos-cp.eu/resources/cpmeta/atc$tracer$dObj> AS ?spec)
			?dobj cpmeta:hasObjectSpec ?spec .
			VALUES ?station {$station} ?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy [
				prov:endedAtTime ?submTime ;
				prov:wasAssociatedWith ?submitter
			] .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
			}
	"""

	//from icoscp Python library
	def drought2018AtmoProductFileInfo(stationLabel: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd ?samplingHeight
		where {
			VALUES ?spec {<http://meta.icos-cp.eu/resources/cpmeta/atcCo2NrtGrowingDataObject>
			<http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject>
			<http://meta.icos-cp.eu/resources/cpmeta/drought2018AtmoProduct>}
			?dobj cpmeta:hasObjectSpec ?spec .
			VALUES ?station {<http://meta.icos-cp.eu/resources/stations/$stationLabel>}
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?dobj cpmeta:hasSizeInBytes ?size .
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
			?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
			?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			OPTIONAL{?dobj cpmeta:wasAcquiredBy / cpmeta:hasSamplingHeight ?samplingHeight}
		}
		order by desc(?submTime)
	"""

	//from icoscp Python library
	val drought2018AtmoProductStations= """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		SELECT (STR(?sName) AS ?Short_name) ?Country ?lat ?lon (STR(?lName) AS ?Long_name) ?height WHERE {
		{
			SELECT DISTINCT ?s ?height WHERE {
			?dobj cpmeta:hasObjectSpec <http://meta.icos-cp.eu/resources/cpmeta/drought2018AtmoProduct>;
				(cpmeta:wasAcquiredBy/prov:wasAssociatedWith) ?s;
				cpmeta:hasSizeInBytes ?size.
			OPTIONAL { ?dobj (cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight) ?height. }
			FILTER(NOT EXISTS { _:b1 cpmeta:isNextVersionOf ?dobj. })
			}
		}
		?s cpmeta:hasStationId ?sName;
			cpmeta:hasName ?lName;
			cpmeta:countryCode ?Country;
			cpmeta:hasLatitude ?lat;
			cpmeta:hasLongitude ?lon.
		}
	"""

	//from icoscp Python library
	def icosCitation(dataObj: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select * where{
		optional{$dataObj cpmeta:hasCitationString ?cit}
		}
	"""

	//from icoscp Python library
	def prodsPerDomain(domain: String) = s"""
		prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select ?specLabel ?spec where{
		?spec cpmeta:hasDataTheme <http://meta.icos-cp.eu/resources/themes/$domain> .
		?spec cpmeta:hasDataLevel ?dataLevel .
		filter(?dataLevel > 0 && ?dataLevel < 3)
		{
			{?spec cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos> }
			UNION
			{
				?spec cpmeta:hasAssociatedProject?/cpmeta:hasKeywords ?keywords
				filter(contains(?keywords, "pre-ICOS"))
			}
		}
		filter exists{?spec cpmeta:containsDataset []}
		filter exists{[] cpmeta:hasObjectSpec ?spec}
		?spec rdfs:label ?specLabel .
		}
		order by ?specLabel
	"""

	//from icoscp Python library
	def prodAvailability(dObjLabels: String) = s"""
		prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?samplingHeight ?specLabel ?timeStart ?timeEnd ?stationId
		where {
			{
				select * where {
					VALUES ?spec {$dObjLabels}
					?dobj cpmeta:hasObjectSpec ?spec .
					?dobj cpmeta:wasAcquiredBy [
						prov:startedAtTime ?timeStart ;
						prov:endedAtTime ?timeEnd ;
						prov:wasAssociatedWith ?station
					] .
					optional {?dobj cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight ?samplingHeight }
					FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
				}
			}
			?station cpmeta:hasStationId ?stationId .
			?spec rdfs:label ?specLabel
		}
	"""

	//run by ETC to find newly uploaded raw data objects
	val ecosystemRawDataQueryForETC = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix cpres: <http://meta.icos-cp.eu/resources/cpmeta/>
		prefix esstation: <http://meta.icos-cp.eu/resources/stations/ES_>
		prefix prov: <http://www.w3.org/ns/prov#>
		prefix xsd: <http://www.w3.org/2001/XMLSchema#>
		select ?pid ?fileName
		where {
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
					FILTER((?ftype = "BM"))
				}
			}
			?dobj cpmeta:hasObjectSpec ?spec .
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith esstation:DE-HoH .
			?dobj cpmeta:wasAcquiredBy/prov:startedAtTime ?startTime .
			FILTER NOT EXISTS{[] cpmeta:isNextVersionOf ?dobj }
			BIND(substr(str(?dobj), strlen(str(?dobj)) - 23) AS ?pid)
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:hasSizeInBytes ?size .
			FILTER((?startTime > "2022-03-01T12:00:00Z"^^xsd:dateTime))
		}
		order by ?startTime
	"""

	//from icos-cp-backend/src/TableFormat.ts in npms project
	//used from portal, dygraph-light and dashboard front-end apps in data project
	val previewSchemaInfo = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		SELECT distinct ?objFormat ?goodFlags ?colName ?valueType ?valFormat ?unit ?qKind ?colTip ?isRegex ?flagColName
		WHERE {
			{
				select ?objFormat (group_concat(?goodFlag; separator=";") as ?goodFlags) where{
					<http://meta.icos-cp.eu/resources/cpmeta/atcRnNrtDataObject> cpmeta:hasFormat ?objFormat .
					optional {?objFormat cpmeta:hasGoodFlagValue ?goodFlag}
				}
				group by ?objFormat
			}
			<http://meta.icos-cp.eu/resources/cpmeta/atcRnNrtDataObject> cpmeta:containsDataset ?dset .
			?dset cpmeta:hasColumn ?column .
			?column cpmeta:hasColumnTitle ?colName ;
				cpmeta:hasValueFormat ?valFormat ;
				cpmeta:hasValueType ?valType .
			optional{?column cpmeta:isRegexColumn ?isRegex}
			optional{
				?flagCol cpmeta:isQualityFlagFor ?column ; cpmeta:hasColumnTitle ?flagColName .
				filter exists { ?dset cpmeta:hasColumn ?flagCol }
			}
			?valType rdfs:label ?valueType .
			optional{?valType rdfs:comment ?colTip }
			optional{
				?valType cpmeta:hasUnit ?unit .
				?valType cpmeta:hasQuantityKind/rdfs:label ?qKind .
			}
		} order by ?colName
	"""

	//from portal app in data project, called for time-series preview
	def listKnownDataObjects(dObj: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?hasNextVersion ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd ?hasVarInfo
		where {
		VALUES ?dobj { $dObj }
		?dobj cpmeta:hasObjectSpec ?spec .
		?dobj cpmeta:hasSizeInBytes ?size .
		?dobj cpmeta:hasName ?fileName .
		?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
		?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
		?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		BIND(EXISTS{[] cpmeta:isNextVersionOf ?dobj} AS ?hasNextVersion)
		OPTIONAL {
			BIND ("true"^^xsd:boolean as ?hasVarInfo)
			filter exists{?dobj cpmeta:hasActualVariable [] }
		}
		}
	"""

	//from dygraph-light front-end app in data project
	def previewTableInfo(dObj: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select distinct ?dobj ?objSpec ?nRows ?fileName ?specLabel ?startedAtTime ?columnNames where {
			values ?dobj { $dObj }
			?dobj cpmeta:hasObjectSpec ?objSpec ;
			cpmeta:hasNumberOfRows ?nRows ;
			cpmeta:hasName ?fileName .
			?objSpec rdfs:label ?specLabel .
			?dobj cpmeta:wasAcquiredBy ?acquisition .
			?acquisition prov:startedAtTime ?startedAtTime
			OPTIONAL{?dobj cpmeta:hasActualColumnNames ?columnNames }
		}
	"""

	//from labeling app
	val stationLabelingList = """
		PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
		SELECT * FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
		FROM <http://meta.icos-cp.eu/resources/stationentry/>
		FROM NAMED <http://meta.icos-cp.eu/resources/stationlabeling/>
		WHERE {
		?owlClass rdfs:subClassOf cpst:Station.
		?s rdf:type ?owlClass.
		{
			{ ?s cpst:hasPi ?pi. }
			UNION
			{ ?s cpst:hasDeputyPi ?pi. }
		}
		?pi cpst:hasEmail ?email.
		?s cpst:hasShortName ?provShortName;
			cpst:hasLongName ?provLongName.
		OPTIONAL { GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/> { ?s cpst:hasShortName ?hasShortName. } }
		OPTIONAL { GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/> { ?s cpst:hasLongName ?hasLongName. } }
		OPTIONAL { GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/> { ?s cpst:hasApplicationStatus ?hasApplicationStatus. } }
		OPTIONAL { GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/> { ?s cpst:hasAppStatusComment ?hasAppStatusComment. } }
		OPTIONAL { GRAPH <http://meta.icos-cp.eu/resources/stationlabeling/> { ?s cpst:hasAppStatusDate ?hasAppStatusDate. } }
		}
	"""

	//from labeling app
	def stationLabelingInfo(station: String) = s"""
		PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
		SELECT * FROM NAMED <http://meta.icos-cp.eu/resources/stationlabeling/>
		FROM NAMED <http://meta.icos-cp.eu/resources/stationentry/>
		WHERE { GRAPH ?g { $station ?p ?o. } }
	"""

	//from labeling app
	def stationLabelingFiles(station: String) = s"""
		PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
		PREFIX cpfls: <http://meta.icos-cp.eu/files/>
		SELECT DISTINCT ?file ?fileType ?fileName FROM <http://meta.icos-cp.eu/resources/stationlabeling/>
		WHERE {
		$station cpst:hasAssociatedFile ?file.
		?file cpfls:hasType ?fileType;
			cpfls:hasName ?fileName.
		}
	"""

	//from Main Data Products Drupal page
	val dataProdObjectSpec = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?station ?samplingHeight ?start ?end
		where {
			VALUES ?spec { <http://meta.icos-cp.eu/resources/cpmeta/atcCoNrtGrowingDataObject> }
			?dobj cpmeta:hasObjectSpec ?spec .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd .
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith/cpmeta:hasName ?station .
			OPTIONAL{?dobj cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight ?samplingHeight} .
			?dobj cpmeta:wasAcquiredBy/prov:startedAtTime ?start .
			?dobj cpmeta:wasAcquiredBy/prov:endedAtTime ?end .
			
		}
		order by ?station ?samplingHeight ?start
	"""

	//from dashboard front-end app in data project
	val dashboardDobjList = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>

		select ?station ?dobj ?dataEnd where{
			?station cpmeta:hasStationId "SVB"^^xsd:string .
			?column cpmeta:hasColumnTitle "co2"^^xsd:string ;
				cpmeta:hasValueType/cpmeta:hasUnit ?unit .
			?spec cpmeta:containsDataset/cpmeta:hasColumn ?column ;
				cpmeta:hasDataLevel "1"^^xsd:integer ;
				cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos> .
			?dobj cpmeta:hasObjectSpec ?spec .
			?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
			?dobj cpmeta:wasAcquiredBy/prov:endedAtTime ?dataEnd .
			?dobj cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight ?samplingHeight .
			filter not exists {[] cpmeta:isNextVersionOf ?dobj}
			filter (?samplingHeight = 150) .
		}
		order by desc(?dataEnd)
		limit 1
	"""

	//from dashboard front-end app in data project
	def dashboardTableInfo(dObj: String) = s"""
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select * where {
		values ?dobj { $dObj }
		?dobj cpmeta:hasObjectSpec ?objSpec ;
		cpmeta:hasNumberOfRows ?nRows ;
		cpmeta:hasName ?fileName .
		?objSpec rdfs:label ?specLabel .
		OPTIONAL{?dobj cpmeta:hasActualColumnNames ?columnNames }
		}
	"""

	//from SchemaOrg
	val findable_L2_L3_specs = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select ?spec
		where{
			VALUES ?level { 2 3 }
			?spec cpmeta:hasDataLevel ?level .
			FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
			FILTER(STRSTARTS(str(?spec), "http://meta.icos-cp.eu/"))
		}
	"""

	//from EtcUploadTransformer
	def sameFilenameDataObjects(fn: String) = s"""
		prefix prov: <http://www.w3.org/ns/prov#>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select distinct ?dobj where{
			?dobj cpmeta:hasName "${fn}" .
			?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submEnd .
		}
		order by desc(?submEnd)
		limit 2
	"""

	//from netcdf front-end app in data project
	val netcdfPreviewAppsOnlyQuery = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select * where{
			<https://meta.icos-cp.eu/objects/OPun_V09Pcat5jomRRF-5o0H> cpmeta:hasObjectSpec ?objSpec .
			?objSpec rdfs:label ?specLabel ;
		}
	"""

	//from MetaClient in data project
	//case-insensitive email match
	val ecoStationsWhereEmailIsPi = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select ?stationId where{
			?pi cpmeta:hasEmail ?email .
			FILTER(regex(?email, "Matthias.Droesler@hswt.de", "i")) .
			?pi cpmeta:hasMembership ?memb .
			?memb cpmeta:hasRole <http://meta.icos-cp.eu/resources/roles/PI> .
			?memb cpmeta:atOrganization ?station .
			?station a cpmeta:ES .
			filter not exists {?memb cpmeta:hasEndTime []}
			?station cpmeta:hasStationId ?stationId .
		}
	"""

	//from MetaClient in data project
	val licenceSetForDataObjectList = """
		select distinct ?lic where{
			values ?dobj {
				<https://meta.icos-cp.eu/objects/dxTFsuvG9sI3M0nGCgYNg22X>
				<https://meta.icos-cp.eu/objects/9v-ug2z3IcLVwDWpcG7ASKpW>
				<https://meta.icos-cp.eu/objects/Nh46W1ylcXrZ8jm18uNc9e73>
			}
			?dobj <http://purl.org/dc/terms/license> ?lic
		}
	"""

	//from IngestionUploadTask in data project
	val ingestionUploadTaskColumnFormats = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select ?colName ?valFormat ?isRegex ?isOptional where{
			<http://meta.icos-cp.eu/resources/cpmeta/etcL2Fluxes> cpmeta:containsDataset ?dataSet .
			?dataSet cpmeta:hasColumn ?column .
			?column cpmeta:hasColumnTitle ?colName .
			?column cpmeta:hasValueFormat ?valFormat .
			OPTIONAL{?column cpmeta:isRegexColumn ?isRegex}
			OPTIONAL{?column cpmeta:isOptionalColumn ?isOptional}
		}
	"""

	//from uploadgui front-end app
	val sitesUploaderStations = """
		PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		PREFIX sitesmeta: <https://meta.fieldsites.se/ontologies/sites/>
		SELECT *
		FROM <https://meta.fieldsites.se/resources/sites/>
		WHERE {
			BIND(<https://meta.fieldsites.se/resources/stations/Svartberget> AS ?station) .
			?station a sitesmeta:Station ; cpmeta:hasName ?name; cpmeta:hasStationId ?id .
		}
		order by ?name
	"""

	//from uploadgui front-end app
	val svartbergetSites = """
		PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		SELECT ?site ?name
		WHERE {
			<https://meta.fieldsites.se/resources/stations/Svartberget> cpmeta:operatesOn ?site .
			?site rdfs:label ?name
		}
		order by ?name
	"""

	//from uploadgui front-end app
	val svartbergetForestSamplPoints = """
		PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		SELECT *
		WHERE {
			<https://meta.fieldsites.se/resources/sites/svartberget-forest> cpmeta:hasSamplingPoint ?point .
			?point rdfs:label ?name .
			?point cpmeta:hasLatitude ?latitude .
			?point cpmeta:hasLongitude ?longitude
		}
		order by ?name
	"""

	//from uploadgui front-end app
	val specAndDatasetKindInfo = """
		PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		SELECT *
		FROM <http://meta.icos-cp.eu/resources/cpmeta/>
		WHERE {
			?spec cpmeta:hasDataLevel ?dataLevel ; rdfs:label ?name ;
				cpmeta:hasDataTheme ?theme ; cpmeta:hasAssociatedProject ?project .
			OPTIONAL{?spec cpmeta:hasKeywords ?keywords}
			OPTIONAL{?project cpmeta:hasKeywords ?projKeywords}
			OPTIONAL{
				?spec cpmeta:containsDataset ?dataset .
				BIND(EXISTS{?dataset a cpmeta:DatasetSpec} as ?isSpatioTemp)
			}
		} order by ?name
	"""

	//from uploadgui front-end app
	val l3spatialCoverages = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select *
		from <http://meta.icos-cp.eu/resources/cpmeta/>
		where{
			{{?cov a cpmeta:SpatialCoverage } union {?cov a cpmeta:LatLonBox}}
			?cov rdfs:label ?label
		}
	"""

	//from uploadgui front-end app
	val datasetVarsInfo = """
		prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select ?label ?title ?valueType ?unit ?optional ?regex
		where{
			<http://meta.icos-cp.eu/resources/cpmeta/biosphereModelingSpatialDataset> cpmeta:hasVariable ?var .
			?var rdfs:label ?label; cpmeta:hasVariableTitle ?title ; cpmeta:hasValueType ?valueTypeRes .
			?valueTypeRes rdfs:label ?valueType .
			optional { ?var cpmeta:isOptionalVariable ?optional }
			optional { ?var cpmeta:isRegexVariable ?regex }
			optional { ?valueTypeRes cpmeta:hasUnit ?unit }
		}
	"""

	val incompleteUploads = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix prov: <http://www.w3.org/ns/prov#>

		select ?dobj ?submTime where{
			?dobj cpmeta:hasName ?fileName .
			?dobj cpmeta:hasObjectSpec ?spec .
			FILTER NOT EXISTS { ?dobj cpmeta:wasSubmittedBy/prov:endedAtTime [] }
			?dobj cpmeta:wasSubmittedBy/prov:startedAtTime ?submTime
		}
		order by desc(?submTime)
	"""
	
	val dataObjsWithCollections = """
		PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		prefix dcterms: <http://purl.org/dc/terms/>
		prefix prov: <http://www.w3.org/ns/prov#>
		select ?dobj ?spec ?coll where{
			{
				?dobj cpmeta:hasObjectSpec ?spec .
				?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station .
				?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
				FILTER( ?submTime >= '2021-05-24T00:00:00.000Z'^^xsd:dateTime && ?submTime <= '2021-05-27T00:00:00.000Z'^^xsd:dateTime)
			}
			?spec cpmeta:hasDataTheme <http://meta.icos-cp.eu/resources/themes/atmosphere> ;
				cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos>;
				cpmeta:hasDataLevel "2"^^xsd:integer .
			#?coll a cpmeta:Collection .
			?coll dcterms:hasPart ?dobj .
		}
	"""

	val geoFilter = """
	prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	prefix xsd: <http://www.w3.org/2001/XMLSchema#>
	prefix geo: <http://www.opengis.net/ont/geosparql#>

	select ?dataType ?dataLevel ?submitter ?count ?station ?site
	where{
		{
		select ?station ?site ?submitter ?spec (count(?dobj) as ?count) where{
			?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith ?submitter .
			?dobj cpmeta:hasObjectSpec ?spec .
			OPTIONAL {?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station }
			OPTIONAL {?dobj cpmeta:wasAcquiredBy/cpmeta:wasPerformedAt ?site }
			?dobj cpmeta:hasSizeInBytes ?size .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?dobj geo:sfIntersects/geo:asWKT "POLYGON((19.045207 68.35593, 19.045208 68.35595, 19.045209 68.35595, 19.045209 68.35593, 19.045207 68.35593))"^^geo:wktLiteral . # Abisko Stordalen station
		}
		group by ?spec ?submitter ?station ?site
	}
	FILTER(CONTAINS(str(?spec), "meta.icos-cp.eu"))
	?spec rdfs:label ?dataType .
	?spec cpmeta:hasDataLevel ?dataLevel .
	}
	"""
}
