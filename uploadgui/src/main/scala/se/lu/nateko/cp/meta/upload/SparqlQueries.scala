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
		| $orgFilter }
		|order by ?name""".stripMargin

	private def icosStations(orgFilter: String) = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <http://meta.icos-cp.eu/resources/icos/>
		|FROM <http://meta.icos-cp.eu/resources/cpmeta/>
		|FROM <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|FROM <http://meta.icos-cp.eu/resources/extrastations/>
		|WHERE {
		| ?station cpmeta:hasName ?name; cpmeta:hasStationId ?id .
		| $orgFilter }
		|order by ?name""".stripMargin

	def stations(orgClass: Option[URI], producingOrg: Option[URI])(implicit envri: Envri): String = {
		val orgClassFilter = orgClass.map(org => s"?station a/rdfs:subClassOf* <$org> .")
		val producingOrgFilter: Option[String] = producingOrg.map(org => s"FILTER(?station = <$org>) .")
		val orgFilter = Iterable(orgClassFilter, producingOrgFilter).flatten.mkString("\n")
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
		|	?site rdfs:label ?name }
		|order by ?name""".stripMargin

	def toSite(b: Binding) = NamedUri(new URI(b("site")), b("name"))

	def samplingpoints(site: URI): String = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|WHERE {
		|	<$site> cpmeta:hasSamplingPoint ?point .
		|	?point rdfs:label ?name .
		|	?point cpmeta:hasLatitude ?latitude .
		|	?point cpmeta:hasLongitude ?longitude }
		|order by ?name""".stripMargin

	def toSamplingPoint(b: Binding) = SamplingPoint(new URI(b("point")), b("latitude").toDouble, b("longitude").toDouble, b("name"))

	private def objSpecsTempl(from: String) = s"""PREFIX cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|SELECT *
		|FROM <${from}>
		|WHERE {
		|	?spec cpmeta:hasDataLevel ?dataLevel ; rdfs:label ?name ;
		|		cpmeta:hasDataTheme ?theme ; cpmeta:hasAssociatedProject ?project .
		|	OPTIONAL{?spec cpmeta:hasKeywords ?keywords}
		|	OPTIONAL{?project cpmeta:hasKeywords ?projKeywords}
		|	OPTIONAL{?spec cpmeta:containsDataset ?dataset}
		|} order by ?name""".stripMargin

	def objSpecs(implicit envri: Envri): String = envri match {
		case Envri.SITES => objSpecsTempl("https://meta.fieldsites.se/resources/sites/")
		case Envri.ICOS => objSpecsTempl("http://meta.icos-cp.eu/resources/cpmeta/")
	}

	def toObjSpec(b: Binding) = {
		def keywords(varname: String) = b.get(varname).map(_.split(",").toSeq).getOrElse(Seq.empty)

		ObjSpec(
			new URI(b("spec")),
			b("name"),
			b("dataLevel").toInt,
			b.contains("dataset"),
			new URI(b("theme")),
			new URI(b("project")),
			keywords("keywords").concat(keywords("projKeywords")).distinct
		)
	}

	//Only for ICOS for now
	def l3spatialCoverages = """|prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select *
		|from <http://meta.icos-cp.eu/resources/cpmeta/>
		|where{
		|	{{?cov a cpmeta:SpatialCoverage } union {?cov a cpmeta:LatLonBox}}
		|	?cov rdfs:label ?label
		|}
		|""".stripMargin

	def toSpatialCoverage(b: Binding) = new SpatialCoverage(new URI(b("cov")), b("label"))

	private def peopleQuery(from: Seq[String]) = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select *
		|${from.map(graph => s"""FROM <$graph>""").mkString("\n")}
		|where{
		|	?pers a cpmeta:Person ;
		|	cpmeta:hasFirstName ?fname ;
		|	cpmeta:hasLastName ?lname .
		|	optional {?pers cpmeta:hasEmail ?email}
		|}""".stripMargin

	def people(implicit envri: Envri): String = envri match {
		case Envri.SITES => peopleQuery(Seq("https://meta.fieldsites.se/resources/sites/"))
		case Envri.ICOS => peopleQuery(Seq("http://meta.icos-cp.eu/resources/cpmeta/", "http://meta.icos-cp.eu/resources/icos/"))
	}

	def toPerson(b: Binding) = NamedUri(new URI(b("pers")), s"""${b("fname")} ${b("lname")}""")

	def organizationsQuery(from: Seq[String]) = s"""prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>
		|prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?org ?orgName ?label
		|${from.map(graph => s"""FROM <$graph>""").mkString("\n")}
		|where{
		|	values ?orgClass {cpmeta:Organization cpmeta:CentralFacility cpmeta:ThematicCenter}
		|	?org a ?orgClass ; cpmeta:hasName ?orgName .
		|	optional {?org rdfs:label ?label }
		|}""".stripMargin

	def organizations(implicit envri: Envri): String = envri match {
		case Envri.SITES => organizationsQuery(Seq("https://meta.fieldsites.se/resources/sites/"))
		case Envri.ICOS => organizationsQuery(Seq("http://meta.icos-cp.eu/resources/cpmeta/", "http://meta.icos-cp.eu/resources/icos/"))
	}

	def toOrganization(b: Binding) = NamedUri(new URI(b("org")), b("orgName"))
}
