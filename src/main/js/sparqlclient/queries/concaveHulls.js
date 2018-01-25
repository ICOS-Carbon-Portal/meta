export const concaveHulls = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select ?station ?nRows ?fileName ?dobj ?plot where{
	?dobj cpmeta:hasSpatialCoverage/cpmeta:asGeoJSON ?geoJson .
	FILTER(CONTAINS(?geoJson, "Polygon"))
	?dobj cpmeta:hasObjectSpec/cpmeta:hasFormat cpmeta:asciiOtcSocatTimeSer .
	?dobj cpmeta:hasName ?fileName .
	?dobj cpmeta:wasAcquiredBy/prov:wasAssociatedWith/cpmeta:hasName ?station .
	?dobj cpmeta:hasNumberOfRows ?nRows
	BIND(CONCAT("https://data.icos-cp.eu/dygraph-light/?objId=", STRAFTER(str(?dobj), "objects/"), "&x=Longitude&y=Latitude&type=scatter") AS ?plot)
}
ORDER BY ?station desc(?nRows)`;

