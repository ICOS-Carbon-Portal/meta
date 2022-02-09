export const existingDocuments = (host) => { return `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select * where{
	?doc a cpmeta:DocumentObject .
	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?doc}
	?doc cpmeta:hasName ?fileName .
	OPTIONAL{?doc cpmeta:hasCitationString ?citation}
	OPTIONAL{?doc cpmeta:hasBiblioInfo ?bibinfo}
	FILTER(STRSTARTS(str(?doc), "${host}"))
}`;
}