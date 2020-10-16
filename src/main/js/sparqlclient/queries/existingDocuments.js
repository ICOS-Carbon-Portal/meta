export const existingDocuments = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select * where{
	?doc a cpmeta:DocumentObject .
	?doc cpmeta:hasName ?fileName .
	OPTIONAL{?doc cpmeta:hasCitationString ?citation}
}`;
