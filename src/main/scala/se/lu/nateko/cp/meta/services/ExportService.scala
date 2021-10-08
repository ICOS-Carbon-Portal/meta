package se.lu.nateko.cp.meta.services

import java.net.URI
import org.eclipse.rdf4j.model.IRI
import spray.json._

import akka.http.scaladsl.server.directives.ContentTypeResolver

import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.utils.rdf4j._
import Envri.{Envri, EnvriConfigs}


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

		val published: JsValue = asOptJsString(dobj.submission.stop.map(_.toString))

		val modified = JsString(
			(dobj.production.map(_.dateTime).toSeq :+ dobj.submission.start).sorted.head.toString
		)

		val keywords = dobj.keywords.fold(JsArray.empty)(k =>
			JsArray(k.map(JsString(_)).toVector)
		)

		val country: Option[CountryCode] = envri match {
			case Envri.SITES => CountryCode.unapply("SE")
			case _ => dobj.specificInfo.toOption.flatMap(
				_.acquisition.station.specificInfo match {
					case iss: IcosStationSpecifics => iss.countryCode
					case _ => None
				}
			)
		}

		val spatialCoverage = dobj.coverage.fold[JsValue](JsNull)(f => geoFeatureToSchemaOrgPlace(f, country))

		val distribution = dobj.accessUrl.fold[JsValue](JsNull){url =>
			val contType = implicitly[ContentTypeResolver].apply(dobj.fileName)
			JsObject(
				"contentUrl"     -> JsString(url.toString),
				"encodingFormat" -> JsString(contType.mediaType.toString)
			)
		}

		val temporalCoverage: JsValue = dobj.specificInfo.fold(
			l3 => timeIntToSchemaOrg(l3.temporal.interval),
			_.acquisition.interval.map(timeIntToSchemaOrg).getOrElse(JsNull)
		)

		val stationCreator = dobj.specificInfo.fold(
			_ => JsNull,
			l2 => orgToSchemaOrg(l2.acquisition.station.org, l2.acquisition.station.responsibleOrganization)
		)

		val creator = envri match {
			case Envri.SITES => stationCreator
			case _ => dobj.references.authors.toSeq.flatten match {
				case Seq() => dobj.production.map(p => agentToSchemaOrg(p.creator)).getOrElse(stationCreator)
				case authors => JsArray(authors.map(agentToSchemaOrg).toVector)
			}
		}

		val publisherLogo: JsValue = envri match {
			case Envri.SITES => JsString("https://static.icos-cp.eu/images/sites-logo.png")
			case Envri.ICOS => JsString("https://static.icos-cp.eu/images/ICOS_RI_logo_rgb.png")
			case _ => JsNull
		}

		val producer = dobj.production.map(p => agentToSchemaOrg(p.host.getOrElse(p.creator))).getOrElse(JsNull)

		val contributor = dobj.production.fold[JsValue](JsNull)(p =>
			JsArray(p.contributors.map(agentToSchemaOrg).toVector)
		)

		val variableMeasured = dobj.specificInfo
			.fold(_.variables, _.columns)
			.fold[JsValue](JsNull)(v => JsArray(
					v.map(variable => {
						JsObject(
							"@type"       -> JsString("PropertyValue"),
							"name"        -> JsString(variable.label),
							"description" -> asOptJsString(variable.valueType.self.label),
							"unitText"    -> asOptJsString(variable.valueType.unit)
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
			"name"                  -> asOptJsString(dobj.references.title),
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
			"distribution"          -> distribution,
			"temporalCoverage"      -> temporalCoverage,
			"publisher"             -> JsObject(
				"@type" -> JsString("Organization"),
				"@id"   -> JsString(conf.dataHost),
				"name"  -> JsString(s"$envri data portal"),
				"url"   -> JsString(s"https://${conf.dataHost}"),
				"logo"  -> publisherLogo
			),
			"producer"              -> producer,
			"provider"              -> agentToSchemaOrg(dobj.submission.submitter),
			"creator"               -> creator,
			"contributor"           -> contributor,
			"variableMeasured"      -> variableMeasured,
			"isPartOf"              -> isPartOf
		)
	}

	def geoFeatureToSchemaOrg(cov: GeoFeature): JsValue = cov match{

		case FeatureCollection(feats, _) => JsArray(
			feats.map(geoFeatureToSchemaOrg).toVector
		)

		case Position(lat, lon, altOpt, _) => JsObject(
			Map(
				"@type"     -> JsString("GeoCoordinates"),
				"latitude"  -> JsNumber(lat),
				"longitude" -> JsNumber(lon)
			) ++ altOpt.map{alt =>
				"elevation" -> JsNumber(alt)
			}
		)

		case Circle(center, radius, _) => JsObject(
			"@type"       -> JsString("GeoCircle"),
			"geoMidpoint" -> geoFeatureToSchemaOrg(center),
			"geoRadius"   -> JsNumber(radius)
		)

		case LatLonBox(min, max, _, _) => JsObject(
			"@type"     -> JsString("GeoShape"),
			"polygon"   -> {
				val (minlat, minlon, maxlat, maxlon) = (min.lat6, min.lon6, max.lat6, max.lon6)
				JsString(s"$minlat $minlon $maxlat $minlon $maxlat $maxlon $minlat $maxlon $minlat $minlon")
			}
		)

		case GeoTrack(points, _) => JsObject(
			"@type"     -> JsString("GeoShape"),
			"polygon"   -> JsString(points.map(p => s"${p.lat6} ${p.lon6}").mkString(" "))
		)

		case Polygon(vertices, _) => JsObject(
			"@type"     -> JsString("GeoShape"),
			"polygon"   -> JsString(vertices.map(p => s"${p.lat6} ${p.lon6}").mkString(" "))
		)
	}

	def orgToSchemaOrg(org: Organization, parent: Option[Organization]) = JsObject(
		Map(
			"@type"  -> JsString("Organization"),
			"@id"    -> JsString(org.self.uri.toString),
			"sameAs" -> JsString(org.self.uri.toString),
			"name"   -> JsString(org.name),
			"email"  -> asOptJsString(org.email),
		) ++ parent.map{ parent =>
			"parentOrganization" -> JsString(parent.name)
		}
	)

	def agentToSchemaOrg(agent: Agent): JsObject = agent match {

		case org: Organization => orgToSchemaOrg(org, None)

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

	def timeIntToSchemaOrg(int: TimeInterval) =  JsString(s"${int.start}/${int.stop}")

	def asOptJsString(sOpt: Option[String]): JsValue = sOpt.fold[JsValue](JsNull)(JsString.apply)

	def countryCodeToSchemaOrg(country: Option[CountryCode]) = country.fold[JsValue](JsNull)(country =>
		JsObject(
			"@type"      -> JsString("Country"),
			"identifier" -> JsString(country.code),
			"name"       -> JsString(country.displayCountry)
		)
	)

	def geoFeatureToSchemaOrgPlace(feature: GeoFeature, country: Option[CountryCode] = None): JsValue = feature match{
		case FeatureCollection(geoms, _) =>
			JsArray(geoms.map(geo => geoFeatureToSchemaOrgPlace(geo, country)).toVector)
		case _ =>
			JsObject(
				"@type"            -> JsString("Place"),
				"name"             -> asOptJsString(feature.label),
				"geo"              -> geoFeatureToSchemaOrg(feature),
				"containedInPlace" -> countryCodeToSchemaOrg(country),
			)
	}
}
