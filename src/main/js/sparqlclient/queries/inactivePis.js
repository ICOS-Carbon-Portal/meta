const stationEntryUriPrefix = "http://meta.icos-cp.eu/resources/stationentry/";
const stationEntryOntUriPrefix = "http://meta.icos-cp.eu/ontologies/stationentry/";
const stationLabelingUriPrevix = "http://meta.icos-cp.eu/resources/stationlabeling/";

export const inactivePis = `prefix stentry: <${stationEntryOntUriPrefix}>

select distinct ?stationTheme ?stationName ?email#(group_concat(distinct ?email; separator = ";") as ?list)
from <${stationEntryOntUriPrefix}>
from named <${stationLabelingUriPrevix}>
from named <${stationEntryUriPrefix}>
where{
	graph <${stationEntryUriPrefix}> {
		?s a ?stationClass .
		?s stentry:hasLongName ?stationName .
		?s stentry:hasPi ?pi .
		?pi stentry:hasEmail ?email .
	}
	filter not exists{
		graph <${stationLabelingUriPrevix}> {
			?s ?p ?o .
		}
	}
	?stationClass rdfs:subClassOf stentry:Station .
	?stationClass rdfs:label ?stationTheme .
}
order by ?stationTheme ?stationName`;
