package se.lu.nateko.cp.meta.upload

import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri

object SparqlQueries {

	type Binding = Map[String, String]

	private val sitesStations = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|PREFIX sitesmeta: <https://meta.fieldsites.se/ontologies/sites/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|FROM <https://meta.fieldsites.se/resources/sites/>
		|WHERE { ?station a sitesmeta:Station ; cpmeta:hasName ?name; cpmeta:hasStationId ?id }""".stripMargin

	private val icosStations = """PREFIX cpst: <http://meta.icos-cp.eu/ontologies/stationentry/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/resources/stationentry/>
		|WHERE { ?station cpst:hasLongName ?name; cpst:hasShortName ?id }
		|order by ?name""".stripMargin

	def stations(implicit envri: Envri): String = envri match {
		case Envri.SITES => sitesStations
		case Envri.ICOS => icosStations
	}

	def toStation(b: Binding) = Station(new URI(b("station")), b("id"), b("name"))

	private val sitesObjSpecs = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|FROM <https://meta.fieldsites.se/resources/sites/>
		|WHERE {
		|	?spec a cpmeta:DataObjectSpec ;
		|		rdfs:label ?name ;
		|		cpmeta:hasDataLevel ?dataLevel .
		|} order by ?name""".stripMargin

	private val icosObjSpecs = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/resources/cpmeta/>
		|WHERE {
		|	?spec a cpmeta:DataObjectSpec ;
		|		rdfs:label ?name ;
		|		cpmeta:hasDataLevel ?dataLevel .
		|} order by ?name""".stripMargin

	def objSpecs(implicit envri: Envri): String = envri match {
		case Envri.SITES => sitesObjSpecs
		case Envri.ICOS => icosObjSpecs
	}

	def toObjSpec(b: Binding) = ObjSpec(new URI(b("spec")), b("name"), b("dataLevel").toInt)
}
