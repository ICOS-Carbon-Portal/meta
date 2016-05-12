
export const activeStations = `prefix stentry: <http://meta.icos-cp.eu/ontologies/stationentry/>

select distinct ?stationTheme ?name ?status
from <http://meta.icos-cp.eu/ontologies/stationentry/>
from named <http://meta.icos-cp.eu/resources/stationlabeling/>
from named <http://meta.icos-cp.eu/resources/stationentry/>
where{
  graph <http://meta.icos-cp.eu/resources/stationentry/> {
    ?s a ?stationClass .
    ?s stentry:hasLongName ?name .
  }
  graph <http://meta.icos-cp.eu/resources/stationlabeling/> {
    ?s ?p ?o .
    optional{?s stentry:hasApplicationStatus ?status}
  }
  ?stationClass rdfs:subClassOf stentry:Station .
  ?stationClass rdfs:label ?stationTheme .
}`;
