
export const etcLabelingValues = `prefix stentry: <http://meta.icos-cp.eu/ontologies/stationentry/>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>

select distinct ?id ?name ?property (str(?o) as ?value)
from <http://meta.icos-cp.eu/ontologies/stationentry/>
from named <http://meta.icos-cp.eu/resources/stationlabeling/>
from named <http://meta.icos-cp.eu/resources/stationentry/>
where{
 graph <http://meta.icos-cp.eu/resources/stationentry/> {
  ?s a stentry:ES .
  ?s stentry:hasLongName ?name .
  ?s stentry:hasShortName ?id .
  ?s stentry:hasStationClass ?sClass .
 }
 graph <http://meta.icos-cp.eu/resources/stationlabeling/> {
  ?s ?p ?o .
 }
 ?p rdfs:label ?property
}`;

