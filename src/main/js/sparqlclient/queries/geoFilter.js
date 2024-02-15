export const geoFilter = (host) => {return `prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
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
#FILTER(?dataLevel < 3)
}
# Abisko Stordalen station
#?dobj geo:sfIntersects/geo:asWKT "POLYGON((19.045207 68.35593, 19.045208 68.35595, 19.045209 68.35595, 19.045209 68.35593, 19.045207 68.35593))"^^geo:wktLiteral .

# US-WPT station (Ohio)
#?dobj geo:sfIntersects/geo:asWKT "POLYGON((-84.8216751814 40.599603385, -80.9935470819 40.599603385, -80.9935470819 42.428823568, -84.8216751814 42.428823568, -84.8216751814 40.599603385))"^^geo:wktLiteral .

# South america
#?dobj geo:sfIntersects/geo:asWKT "POLYGON((-81.4502 -55.2591, -81.4502 12.4371, -34.7296 12.4371, -34.7296 -55.2591, -81.4502 -55.2591))"^^geo:wktLiteral .

# La Reunión
#?dobj geo:sfIntersects/geo:asWKT "POLYGON((55.3840 -21.0795, 55.3841 -21.0797, 55.3842 -21.0797, 55.3842 -21.0795, 55.3840 -21.0795))"^^geo:wktLiteral .
`;
}