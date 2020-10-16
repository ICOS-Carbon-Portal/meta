export const stationRoles = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select ?stId ?stationName ?fname ?lname ?roleName ?weight ?start ?end
from <http://meta.icos-cp.eu/resources/icos/>
from <http://meta.icos-cp.eu/resources/cpmeta/>
where{
	?pers cpmeta:hasMembership ?memb .
	?memb cpmeta:hasRole/rdfs:label ?roleName .
	?memb cpmeta:atOrganization ?station .
	?station cpmeta:hasStationId ?stId ; cpmeta:hasName ?stationName .
	?pers cpmeta:hasFirstName ?fname; cpmeta:hasLastName ?lname .
	OPTIONAL{?memb cpmeta:hasStartTime ?start}
	OPTIONAL{?memb cpmeta:hasEndTime ?end}
	OPTIONAL{?memb cpmeta:hasAttributionWeight ?weight}
}
order by ?stId desc(?weight) desc(?end) ?start ?roleName ?lname`;