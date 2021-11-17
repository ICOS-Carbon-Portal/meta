export const perFormatStats = `prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
select (count(?dobj) as ?c) ?format #?size
where{
	?dobj a cpmeta:DataObject .
	?dobj cpmeta:hasSizeInBytes ?size.
	?dobj cpmeta:hasObjectSpec/cpmeta:hasFormat ?format .
	filter (?format != cpmeta:asciiWdcggTimeSer)
}
group by ?format`;
