export const atcSpeciesAndHeights = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select ?station ?stationId ?lat ?lon ?stationName ?speciesList ?heightsList ?speciesHeightsList where{
	{
		select
			?station
			(group_concat(distinct ?varName; separator='|') as ?speciesList)
			(group_concat(distinct ?height; separator='|') as ?heightsList)
			(group_concat(distinct ?speciesHeight; separator='|') as ?speciesHeightsList)
		where{
			?spec cpmeta:hasDataTheme <http://meta.icos-cp.eu/resources/themes/atmosphere> ;
				cpmeta:hasAssociatedProject <http://meta.icos-cp.eu/resources/projects/icos> ;
			cpmeta:containsDataset ?ds .
			filter(?ds != <http://meta.icos-cp.eu/resources/cpmeta/atcMeteoTimeSer>)
			?ds cpmeta:hasColumn ?col .
			filter exists {[] cpmeta:isQualityFlagFor ?col}
			?dobj cpmeta:hasObjectSpec ?spec ;
					cpmeta:wasAcquiredBy/prov:wasAssociatedWith ?station ;
					cpmeta:hasSizeInBytes ?size ;
					cpmeta:wasAcquiredBy/cpmeta:hasSamplingHeight ?height .
			FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
			?col cpmeta:hasColumnTitle ?varName .
			{
				{FILTER NOT EXISTS {?dobj cpmeta:hasVariableName ?actVar}}
				UNION
				{
					?dobj cpmeta:hasVariableName ?actVar
					filter(?actVar = ?varName)
				}
			}
			bind(concat(?varName, '/', str(?height)) as ?speciesHeight)
		}
		group by ?station
	}
	?station cpmeta:hasStationId ?stationId ; cpmeta:hasName ?stationName ;
	cpmeta:hasLatitude ?lat ; cpmeta:hasLongitude ?lon .
}
order by ?stationId`;
