package se.lu.nateko.cp.meta.upload

import java.net.URI

object SparqlQueries {

	type Binding = Map[String, String]

	val sitesStations = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|PREFIX sitesmeta: <https://meta.fieldsites.se/ontologies/sites/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|FROM <https://meta.fieldsites.se/resources/sites/>
		|WHERE { ?station a sitesmeta:Station ; cpmeta:hasName ?name; cpmeta:hasStationId ?id }""".stripMargin

	def toStation(b: Binding) = Station(new URI(b("station")), b("id"), b("name"))

	val sitesObjSpecs = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|FROM <https://meta.fieldsites.se/resources/sites/>
		|WHERE {
		|	?spec a cpmeta:DataObjectSpec ;
		|		rdfs:label ?name ;
		|		cpmeta:hasDataLevel ?dataLevel .
		|} order by ?name""".stripMargin

	def toObjSpec(b: Binding) = ObjSpec(new URI(b("spec")), b("name"), b("dataLevel").toInt)
}
