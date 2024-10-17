export const hoVsTc = `PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
SELECT ?tcId ?hoId ?tcName ?hoName ?tcClass ?hoClass ?tcPIs ?hoPIs
FROM <http://meta.icos-cp.eu/resources/stationentry/>
FROM <http://meta.icos-cp.eu/ontologies/stationentry/>
FROM <http://meta.icos-cp.eu/resources/icos/>
WHERE {
	{
		select
			?tcS (sample(?tcId0) as ?tcId) (sample(?tcClass0) as ?tcClass)
			(sample(?tcName0) as ?tcName) (group_concat(?lname; separator=';') as ?tcPIs)
		where{
			?tcS cpmeta:hasStationId ?tcId0 .
			optional{?tcS cpmeta:hasStationClass ?tcClass0 }
			optional{?tcS cpmeta:hasName ?tcName0 }
			optional{
				?memb cpmeta:atOrganization ?tcS .
				?memb cpmeta:hasRole <http://meta.icos-cp.eu/resources/roles/PI> .
				optional{?memb cpmeta:hasEndTime ?end}
				filter(!bound(?end))
				?pi cpmeta:hasMembership ?memb ; cpmeta:hasLastName ?lname .
			}
		}
		group by ?tcS
	}
	{
		select
			?hoS (sample(?hoId0) as ?hoId) (sample(?hoName0) as ?hoName)
			(sample(if(contains(?hoClass0, 'Ass'), 'Associated', ?hoClass0)) as ?hoClass)
			(group_concat(?lname; separator=';') as ?hoPIs)
		where{
			?hoS cpst:hasShortName ?hoId0 .
			optional{?hoS cpst:hasLongName ?hoName0 }
			optional{?hoS cpst:hasStationClass ?hoClass0 }
			optional{?hoS cpst:hasPi/cpst:hasLastName ?lname }
		}
		group by ?hoS
	}
	?tcS a ?stationTheme .
	?hoS cpst:hasProductionCounterpart ?tsUriStr .
	filter(iri(?tsUriStr) = ?tcS)
	filter(?tcId != ?hoId || ?tcName != ?hoName || ?tcClass != ?hoClass || ?tcPIs != ?hoPIs)
}
order by ?stationTheme ?tcId`;
