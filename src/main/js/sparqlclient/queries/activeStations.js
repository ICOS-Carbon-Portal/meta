
export const activeStations = `prefix stentry: <http://meta.icos-cp.eu/ontologies/stationentry/>

select distinct ?stationTheme ?name ?status
from <http://meta.icos-cp.eu/ontologies/stationsschema/>
from named <http://meta.icos-cp.eu/ontologies/stationlabeling/>
from named <http://meta.icos-cp.eu/ontologies/stationentry/>
where{
  graph <http://meta.icos-cp.eu/ontologies/stationentry/> {
    ?s a ?stationClass .
    ?s stentry:hasLongName ?name .
  }
  graph <http://meta.icos-cp.eu/ontologies/stationlabeling/> {
    ?s ?p ?o .
    optional{?s stentry:hasApplicationStatus ?status}
  }
  ?stationClass rdfs:subClassOf stentry:Station .
  ?stationClass rdfs:label ?stationTheme .
}`;
