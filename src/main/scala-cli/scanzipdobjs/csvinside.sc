//> using toolkit default

import sttp.client4.quick.*

val query = """prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
prefix prov: <http://www.w3.org/ns/prov#>
select ?dobj ?fileName ?size ?submTime
where {
	?dobj cpmeta:hasObjectSpec <http://meta.icos-cp.eu/resources/cpmeta/etcEddyFluxRawSeriesCsv> .
	?dobj cpmeta:wasSubmittedBy/prov:wasAssociatedWith <http://meta.icos-cp.eu/resources/stations/ES_FR-Fon> .
	?dobj cpmeta:hasSizeInBytes ?size .
	?dobj cpmeta:hasName ?fileName .
	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
	filter(strends(?fileName, "03.zip") || strends(?fileName, "02.zip"))
}
order by desc(?submTime)
"""

case class Dobj(uri: String, fileName: String)

def dobjsToScan: Seq[Dobj] =
	val qResp = quickRequest
		.post(uri"https://meta.icos-cp.eu/sparql")
		.header("Accept", "text/csv")
		.body(query)
		.send()
	if qResp.code.code != 200 then
		throw Error("SPARQL error:\n" + qResp.body)
	else
		qResp.body.linesIterator
			.drop(1) // header line with SPARQL variables
			.map: line =>
				val parts = line.split(",")
				Dobj(parts(0), parts(1))
			.toIndexedSeq

def listZipDobjFilenames(dobj: Dobj): Seq[String] =
	val hash = dobj.uri.split("/").last
	val resp = quickRequest
		.get(uri"https://data.icos-cp.eu/zip/${hash}/listContents")
		.send()
	ujson.read(resp.body).arr.map(v => v("name").str).toSeq

def badECfiles = dobjsToScan.iterator.filter: dobj =>
	listZipDobjFilenames(dobj).exists(_.endsWith(".csv"))

badECfiles.foreach: dobj =>
	println(s"<${dobj.uri}>") //\t${dobj.fileName}")