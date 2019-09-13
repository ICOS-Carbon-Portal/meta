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

	def sites(station: URI): String = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT ?site ?name
		|WHERE {
		|	<$station> cpmeta:operatesOn ?site .
		|	?site rdfs:label ?name .
		|}""".stripMargin

	def toSite(b: Binding) = Site(new URI(b("site")), b("name"))

	private def objSpecsTempl(from: String) = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <${from}>
		|WHERE {
		|	?spec cpmeta:hasDataLevel ?dataLevel ; rdfs:label ?name .
		|	OPTIONAL{?spec cpmeta:containsDataset ?dataset}
		|} order by ?name""".stripMargin

	def objSpecs(implicit envri: Envri): String = envri match {
		case Envri.SITES => objSpecsTempl("https://meta.fieldsites.se/resources/sites/")
		case Envri.ICOS => objSpecsTempl("http://meta.icos-cp.eu/resources/cpmeta/")
	}

	def toObjSpec(b: Binding) = ObjSpec(new URI(b("spec")), b("name"), b("dataLevel").toInt, b.contains("dataset"))
}
