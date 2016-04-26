
export const stationPis = `prefix st: <http://meta.icos-cp.eu/ontologies/stationentry/>
select distinct ?stationId ?stationName ?firstName ?lastName ?email
from <http://meta.icos-cp.eu/ontologies/stationentry/>
where{
  ?s a st:ES .
  ?s st:hasShortName ?stationId .
  ?s st:hasLongName ?stationName .
  ?s st:hasPi ?pi .
  ?pi st:hasFirstName ?firstName .
  ?pi st:hasLastName ?lastName .
  ?pi st:hasEmail ?email .
}
order by ?lastName`;

export const etcClass1And2Pis = `prefix st: <http://meta.icos-cp.eu/ontologies/stationentry/>
select distinct ?stationId ?stationClass ?firstName ?lastName ?email
from <http://meta.icos-cp.eu/ontologies/stationentry/>
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
