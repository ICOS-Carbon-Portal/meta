const stationEntryUriPrefix = "http://meta.icos-cp.eu/resources/stationentry/";
const stationEntryOntUriPrefix = "http://meta.icos-cp.eu/ontologies/stationentry/";

export const stationPis = `prefix st: <${stationEntryOntUriPrefix}>
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
order by UCASE(?lastName) ?firstName ?stationId`;

export const etcClass1And2Pis = `prefix st: <${stationEntryOntUriPrefix}>
select distinct ?stationId ?stationClass ?firstName ?lastName ?email
from <${stationEntryUriPrefix}>
where{
  ?s a st:ES .
  ?s st:hasShortName ?stationId .
  ?s st:hasStationClass ?stationClass .
  ?s st:hasPi ?pi .
  ?pi st:hasFirstName ?firstName .
  ?pi st:hasLastName ?lastName .
  ?pi st:hasEmail ?email .
  filter (?stationClass = "1" || ?stationClass = "2")
}`;
