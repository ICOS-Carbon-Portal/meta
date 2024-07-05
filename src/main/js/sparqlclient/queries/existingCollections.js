export const existingCollections = (host) => { return `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix dcterms: <http://purl.org/dc/terms/>
select * where{
	?coll a cpmeta:Collection .
	OPTIONAL{?coll cpmeta:hasDoi ?doi}
	?coll dcterms:title ?title .
	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?coll}
	OPTIONAL{?coll cpmeta:hasCitationString ?citation}
	OPTIONAL{?doc cpmeta:hasBiblioInfo ?bibinfo}
	FILTER(CONTAINS(str(?coll), "${host}"))
}
order by ?title
`;
}
