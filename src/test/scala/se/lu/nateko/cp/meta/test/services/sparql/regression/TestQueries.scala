package se.lu.nateko.cp.meta.test.services.sparql.regression

object TestQueries {
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
				FILTER EXISTS {[] cpmeta:hasObjectSpec ?spec}
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

	val objectSpec = (spec: String, station: String) => s"""
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
	order by ?station ?samplingHeight"""

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

	val collectionItems = (collId: String) => s"""
	select * where{ $collId <http://purl.org/dc/terms/hasPart> ?dobj}
	"""

	val stationData = (station: String, level: Int) => s"""
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

	val stations = (id: String) => s"""
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

	val dataObjStation = (dObj: String) => s"""
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
		order by ?Short_name
	"""

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

	val ATCStationList = (station: String, tracer: String, dObj: String) => s"""
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

	val drought2018AtmoProductFileInfo = (stationLabel: String) => s"""
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

	val icosCitation = (dataObj: String) => s"""
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	select * where{
	optional{$dataObj cpmeta:hasCitationString ?cit}
	}
	"""

	val prodsPerDomain = (domain: String) => s"""
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

	val prodAvailability = (dObjLabels: String) => s"""
	prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
	prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
	prefix prov: <http://www.w3.org/ns/prov#>
	select ?dobj ?samplingHeight ?specLabel ?timeStart ?timeEnd ?stationId
	where {
		{
			select * where {
				VALUES ?spec {$dObjLabels
				}
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
	""".stripMargin
}
