export const dcat = `prefix xsd: <http://www.w3.org/2001/XMLSchema#>
prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
prefix dc: <http://purl.org/dc/elements/1.1/>
prefix dcat: <http://www.w3.org/ns/dcat#>
prefix dct: <http://purl.org/dc/terms/>
select * where{
	?cat a dcat:Catalog ;
		dct:description ?catDescr;
		dct:title ?catTitle ;
		dcat:distribution ?catDistr ;
		dcat:dataset ?ds .

	?catDistr a dcat:Distribution ;
		dcat:accessURL ?catAccessUrl ;
		dcat:mediaType ?catAccessMediaType .

	?ds a dcat:Dataset .
	?ds dcat:landingPage ?dobj .
	?ds dct:title ?fileName .
	?ds dct:issued ?submEnd .
	?ds dct:modified ?modified .
	?ds dct:description ?description .
	?ds dct:creator ?creator .
	?ds dct:temporal [
		a dct:PeriodOfTime ;
		dcat:startDate ?timeStart ;
		dcat:endDate ?timeEnd
	].
	?ds dcat:distribution [
		a dcat:Distribution ;
		dcat:accessURL ?accessUrl ;
		dct:license ?licence ;
		dcat:byteSize ?byteSize ;
		dcat:downloadURL ?downloadUrl
	].
}
`;