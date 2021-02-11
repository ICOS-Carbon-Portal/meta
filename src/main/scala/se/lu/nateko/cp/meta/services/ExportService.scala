package se.lu.nateko.cp.meta.services

import java.net.URI
import org.eclipse.rdf4j.model.IRI
import spray.json._

import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeometryCollection
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.staticObjLandingPage
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.core.data.GeoTrack
import se.lu.nateko.cp.meta.core.data.Polygon
import se.lu.nateko.cp.meta.core.data.Agent
import se.lu.nateko.cp.meta.core.data.Organization
import se.lu.nateko.cp.meta.core.data.Person


object ExportService{

	def dataObjs(sparqler: SparqlRunner)(implicit configs: EnvriConfigs, envri: Envri): Seq[URI] = {

		val metaItemPrefix = configs(envri).metaItemPrefix

		val specsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?spec
		|where{
		|	VALUES ?level { 2 3 }
		|	?spec cpmeta:hasDataLevel ?level .
		|	FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
		|	FILTER(STRSTARTS(str(?spec), "$metaItemPrefix"))
		|}""".stripMargin

		val specs: Iterator[String] = sparqler.evaluateTupleQuery(SparqlQuery(specsQuery)).flatMap(b =>
			Option(b.getValue("spec")).map(_.stringValue)
		)

		val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|prefix prov: <http://www.w3.org/ns/prov#>
		|select ?dobj where {
		|	VALUES ?spec {${specs.mkString("<", "> <", ">")}}
		|	?dobj cpmeta:hasObjectSpec ?spec .
		|	?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
		|}
		|order by desc(?submTime)""".stripMargin

		sparqler
			.evaluateTupleQuery(SparqlQuery(query))
			.map(_.getValue("dobj"))
			.collect{case iri: IRI => iri.toJava}
			.toIndexedSeq
	}

	def schemaOrg(dobj: DataObject, handleProxies: HandleProxiesConfig)(implicit conf: EnvriConfig, envri: Envri): JsObject = {

		val landingPage = JsString(staticObjLandingPage(dobj.hash)(conf).toString)

		val title: String = dobj.specificInfo.fold(
			l3 => l3.title,
			l2 => {

				val specName = uriResourceLabel(dobj.specification.self)
				val stationName = l2.acquisition.station.org.name

				l2.acquisition.site.fold(s"$specName from $stationName"){site =>

					val siteName = site.location.flatMap(_.label).orElse(site.self.label)

					val origin = siteName.fold(stationName){site => s"$site ($stationName)"}

					s"$specName from $origin"
				}
			}
		)

		val description: JsValue = {
			val l3Descr = dobj.specificInfo.left.toSeq.flatMap(_.description)
			val specComments = dobj.specification.self.comments
			val prodComment = dobj.production.flatMap(_.comment)
			val citation = dobj.references.citationString
			val allDescrs = (l3Descr ++ specComments ++ prodComment ++ citation).map(_.trim).filter(_.length > 0)
			if(allDescrs.isEmpty) JsNull else JsString(allDescrs.mkString("\n"))
		}

		val identifier: JsValue = {
			val ids: Vector[JsString] = Vector(
				dobj.doi -> handleProxies.doi,
				dobj.pid -> handleProxies.basic
			).collect{
				case (Some(id), proxy) => JsString(proxy.toString + id)
			}
			ids match{
				case Vector() => JsNull
				case Vector(single) => single
				case _ => JsArray(ids)
			}
		}

		val published: JsValue = dobj.submission.stop.fold[JsValue](JsNull)(stop => JsString(stop.toString))

		val modified = JsString(
			(dobj.production.map(_.dateTime).toSeq :+ dobj.submission.start).sorted.head.toString
		)

		val keywords = dobj.keywords.fold[JsValue](JsNull)(k =>
			JsArray(k.map(JsString(_)).toVector)
		)

		val spatialCoverage = dobj.coverage.fold[JsValue](JsNull){cov =>
			JsObject(
				"@type" -> JsString("Place"),
				"geo"   -> coverageToSchemaOrg(cov)
			)
		}

		val temporalCoverage: JsValue = dobj.specificInfo.fold(
			l3 => JsString(s"${l3.temporal.interval.start}/${l3.temporal.interval.stop}"),
			l2 => l2.acquisition.interval.fold[JsValue](JsNull)(interval =>
				JsString(s"${interval.start}/${interval.stop}")
			)
		)

		val accessUrl = dobj.accessUrl.fold[JsValue](JsNull)(url => JsString(url.toString))

		val stationCreator = dobj.specificInfo.fold(_ => JsNull, l2 => {
			val station = l2.acquisition.station
			JsObject(
				"@type"  -> JsString("Organization"),
				"@id"    -> JsString(station.org.self.uri.toString),
				"sameAs" -> JsString(station.org.self.uri.toString),
				"name"   -> JsString(station.org.name),
				"email"  -> JsString(station.org.email.getOrElse(""))
			)
		})

		def agentTemplate(agent: Agent) = agent match {
			case Organization(self, name, email, _) => {
				JsObject(
					"@type"  -> JsString("Organization"),
					"@id"    -> JsString(self.uri.toString),
					"sameAs" -> JsString(self.uri.toString),
					"name"   -> JsString(name),
					"email"  -> JsString(email.getOrElse(""))
				)
			}
			case Person(self, firstName, lastName, _) => {
				JsObject(
					"@type"      -> JsString("Person"),
					"@id"        -> JsString(self.uri.toString),
					"sameAs"     -> JsString(self.uri.toString),
					"givenName"  -> JsString(firstName),
					"familyName" -> JsString(lastName),
					"name"       -> JsString(s"$firstName $lastName")
				)
			}
		}

		val creator = envri match {
			case Envri.SITES => stationCreator
			case _ => dobj.references.authors match {
				case None | Some(Seq()) => dobj.production.map(p => agentTemplate(p.creator)).getOrElse(stationCreator)
				case Some(authors) => JsArray(authors.map(agentTemplate).toVector)
			}
		}

		val producer = dobj.production.fold[JsValue](JsNull)(p =>
			p.host.fold[JsValue](JsNull)(agentTemplate)
		)

		val contributor = dobj.production.fold[JsValue](JsNull)(p =>
			JsArray(p.contributors.map(agentTemplate).toVector)
		)

		val variableMeasured = dobj.specificInfo
			.fold(_.variables, _.columns)
			.fold[JsValue](JsNull)(v => JsArray(
					v.map(variable => {
						JsObject(
							"@type"       -> JsString("PropertyValue"),
							"name"        -> JsString(variable.label),
							"description" -> JsString(variable.valueType.self.label.getOrElse("")),
							"unitText"    -> JsString(variable.valueType.unit.getOrElse(""))
						)
					}).toVector)
		)

		val isPartOf = JsArray(dobj.parentCollections.map(coll =>
			JsString(coll.uri.toString)).toVector
		)

		JsObject(
			"@context"              -> JsString("https://schema.org"),
			"@type"                 -> JsString("Dataset"),
			"@id"                   -> landingPage,
			"name"                  -> JsString(title),
			"alternateName"         -> JsString(dobj.fileName),
			"description"           -> description,
			"url"                   -> landingPage,
			"identifier"            -> identifier,
			"inLanguage"            -> JsArray(
				JsObject(
					"@type" -> JsString("Language"),
					"name"  -> JsString("English")
				)
			),
			"includedInDataCatalog" -> JsObject(
				"@type" -> JsString("DataCatalog"),
				"name"  -> JsString(conf.dataHost)
			),
			"license"               -> JsString("https://creativecommons.org/licenses/by/4.0/"),
			"datePublished"         -> published,
			"dateModified"          -> modified,
			"keywords"              -> keywords,
			"spatialCoverage"       -> spatialCoverage,
			"distribution"          -> JsObject(
				"contentUrl"          -> accessUrl
			),
			"temporalCoverage"      -> temporalCoverage,
			"publisher"             -> JsObject(
				"@type" -> JsString("Organization"),
				"@id"   -> JsString(conf.dataHost),
				"name"  -> JsString(s"$envri data portal"),
				"url"   -> JsString(conf.dataHost)
			),
			"producer"              -> producer,
			"provider"              -> agentTemplate(dobj.submission.submitter),
			"creator"               -> creator,
			"contributor"           -> contributor,
			"variableMeasured"      -> variableMeasured,
			"isPartOf"              -> isPartOf
		)
	}

	private def uriResourceLabel(res: UriResource): String = res.label.getOrElse(res.uri.toString.split("/").last)

	def coverageToSchemaOrg(cov: GeoFeature): JsValue = cov match{

		case GeometryCollection(geos) => JsArray(
			geos.map(coverageToSchemaOrg).toVector
		)

		case Position(lat, lon, altOpt) => JsObject(
			Map(
				"@type"     -> JsString("GeoCoordinates"),
				"latitude"  -> JsNumber(lat),
				"longitude" -> JsNumber(lon)
			) ++ altOpt.map{alt =>
				"elevation" -> JsNumber(alt)
			}
		)

		case LatLonBox(min, max, _, _) => JsObject(
			Map(
				"@type"     -> JsString("GeoShape"),
				"polygon"   -> JsString(s"${min.lat} ${min.lon} ${max.lat} ${min.lon} ${max.lat} ${max.lon} ${min.lat} ${max.lon} ${min.lat} ${min.lon}")
			)
		)

		case GeoTrack(points) => JsObject(
			Map(
				"@type"     -> JsString("GeoShape"),
				"polygon"   -> JsString(points.map(p => s"${p.lat} ${p.lon}").mkString(" "))
			)
		)

		case Polygon(vertices) => JsObject(
			Map(
				"@type"     -> JsString("GeoShape"),
				"polygon"   -> JsString(vertices.map(p => s"${p.lat} ${p.lon}").mkString(" "))
			)
		)
	}

}
