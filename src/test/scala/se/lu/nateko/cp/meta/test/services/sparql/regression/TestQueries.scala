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
}
