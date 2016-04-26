
export const etcLabelingValues = `prefix stentry: <http://meta.icos-cp.eu/ontologies/stationentry/>
select distinct ?id ?name ?property (str(?o) as ?value)
from <http://meta.icos-cp.eu/ontologies/stationsschema/>
from named <http://meta.icos-cp.eu/ontologies/stationlabeling/>
from named <http://meta.icos-cp.eu/ontologies/stationentry/>
where{
 graph <http://meta.icos-cp.eu/ontologies/stationentry/> {
  ?s a stentry:ES .
  ?s stentry:hasLongName ?name .
  ?s stentry:hasShortName ?id .
  ?s stentry:hasStationClass ?sClass .
 }
 graph <http://meta.icos-cp.eu/ontologies/stationlabeling/> {
  ?s ?p ?o .
 }
 ?p rdfs:label ?property
}`;

