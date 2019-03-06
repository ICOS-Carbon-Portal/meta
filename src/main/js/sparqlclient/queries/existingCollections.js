export const existingCollections = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix dcterms: <http://purl.org/dc/terms/>
select * where{
	?coll a cpmeta:Collection .
	OPTIONAL{?coll cpmeta:hasDoi ?doi}
	?coll dcterms:title ?title .
}
`;
