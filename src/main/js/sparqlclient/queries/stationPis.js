const stationEntryUriPrefix = "http://meta.icos-cp.eu/resources/stationentry/";
const stationEntryOntUriPrefix = "http://meta.icos-cp.eu/ontologies/stationentry/";

export const provStationPis = `prefix st: <${stationEntryOntUriPrefix}>
select distinct ?stationTheme ?stationId ?stationName ?firstName ?lastName ?email
from <${stationEntryUriPrefix}>
where{
  ?s st:hasShortName ?stationId .
  ?s st:hasLongName ?stationName .
  ?s st:hasPi ?pi .
  ?pi st:hasFirstName ?firstName .
  ?pi st:hasLastName ?lastName .
  ?pi st:hasEmail ?email .
  ?s a ?stationClass .
  BIND (replace(str(?stationClass), "${stationEntryOntUriPrefix}", "") AS ?stationTheme )
}
order by ?stationTheme ?stationId UCASE(?lastName) ?firstName`;

export const prodStationPis = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
select distinct ?stTheme ?stId ?stClass ?country ?stationName ?fname ?lname ?email
from <http://meta.icos-cp.eu/resources/icos/>
from <http://meta.icos-cp.eu/resources/cpmeta/>
from <http://meta.icos-cp.eu/ontologies/cpmeta/>
where{
	?pers cpmeta:hasMembership ?memb .
	filter not exists{?memb cpmeta:hasEndTime ?end}
	?memb cpmeta:hasRole <http://meta.icos-cp.eu/resources/roles/PI> .
	?memb cpmeta:atOrganization ?station .
	?station cpmeta:hasStationId ?stId ; cpmeta:hasName ?stationName ; cpmeta:countryCode ?country .
	?station a/rdfs:label ?stTheme .
	?pers cpmeta:hasFirstName ?fname; cpmeta:hasLastName ?lname .
	OPTIONAL{?pers cpmeta:hasEmail ?email}
	OPTIONAL{?station cpmeta:hasStationClass ?stClass }
}
order by ?stTheme ?stId ?lname`;
