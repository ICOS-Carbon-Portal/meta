package se.lu.nateko.cp.meta.upload

import java.net.URI

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.Envri

object SparqlQueries {

	type Binding = Map[String, String]

	private def sitesStations(orgFilter: String) = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|PREFIX sitesmeta: <https://meta.fieldsites.se/ontologies/sites/>
		|SELECT *
		|FROM <https://meta.fieldsites.se/resources/sites/>
		|WHERE { ?station a sitesmeta:Station ; cpmeta:hasName ?name; cpmeta:hasStationId ?id .
		| $orgFilter }""".stripMargin

	private def icosStations(orgFilter: String) = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/resources/stations/>
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|WHERE {
		| ?station cpmeta:hasName ?name; cpmeta:hasStationId ?id .
		| $orgFilter }
		|order by ?name""".stripMargin

	def stations(orgClass: Option[URI])(implicit envri: Envri): String = {
		val orgFilter = orgClass.fold("")(org => s"?station a/rdfs:subClassOf* <$org> .")
		envri match {
			case Envri.SITES => sitesStations(orgFilter)
			case Envri.ICOS => icosStations(orgFilter)
		}
	}

	def toStation(b: Binding) = Station(new URI(b("station")), b("id"), b("name"))

	private val sitesObjSpecs = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <https://meta.fieldsites.se/resources/sites/>
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|WHERE {
		|	?spec a/rdfs:subClassOf* cpmeta:DataObjectSpec ;
		|		rdfs:label ?name ;
		|		cpmeta:hasDataLevel ?dataLevel .
		|} order by ?name""".stripMargin

	private val icosObjSpecs = """PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/resources/cpmeta/>
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|WHERE {
		|	?spec a/rdfs:subClassOf* cpmeta:DataObjectSpec ;
		|		rdfs:label ?name ;
		|		cpmeta:hasDataLevel ?dataLevel .
		|} order by ?name""".stripMargin

	def objSpecs(implicit envri: Envri): String = envri match {
		case Envri.SITES => sitesObjSpecs
		case Envri.ICOS => icosObjSpecs
	}

	def toObjSpec(b: Binding) = ObjSpec(new URI(b("spec")), b("name"), b("dataLevel").toInt)
}
