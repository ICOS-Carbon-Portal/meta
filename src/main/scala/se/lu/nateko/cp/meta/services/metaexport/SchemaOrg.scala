package se.lu.nateko.cp.meta.services.metaexport

import java.net.URI
import org.eclipse.rdf4j.model.IRI
import spray.json.*

import akka.http.scaladsl.server.directives.ContentTypeResolver

import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.doi.meta.Person
import se.lu.nateko.cp.meta.views.LandingPageHelpers.getDoiPersonUrl
import se.lu.nateko.cp.doi.meta.Affiliation
import se.lu.nateko.cp.doi.meta.Description


object SchemaOrg:

	def runSparqlQuery(sparqler: SparqlRunner, query: String, value: String) = sparqler
		.evaluateTupleQuery(SparqlQuery(query))
		.map(_.getValue(value))
		.collect{case iri: IRI => iri.toJava}
		.toIndexedSeq

	def collObjs(sparqler: SparqlRunner)(using configs: EnvriConfigs, envri: Envri): Seq[URI] =

		val host = configs(envri).metaHost

		val collsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|prefix dcterms: <http://purl.org/dc/terms/>
		|select * where{
		|	?coll a cpmeta:Collection .
		|	OPTIONAL{?coll cpmeta:hasDoi ?doi}
		|	?coll dcterms:title ?title .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?coll}
		|	OPTIONAL{?coll cpmeta:hasCitationString ?citation}
		|	OPTIONAL{?doc cpmeta:hasBiblioInfo ?bibinfo}
  		|	FILTER(STRSTARTS(str(?coll), "https://meta.icos-cp.eu/"))
		|}
		|order by ?title""".stripMargin
		// using "${host}" will only work in prod?

		runSparqlQuery(sparqler, collsQuery, "coll")

	def docObjs(sparqler: SparqlRunner)(using configs: EnvriConfigs, envri: Envri): Seq[URI] =

		val host = configs(envri).metaHost

		val docsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select * where{
		|	?doc a cpmeta:DocumentObject .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?doc}
		|	?doc cpmeta:hasName ?fileName .
		|	OPTIONAL{?doc cpmeta:hasCitationString ?citation}
		|	OPTIONAL{?doc cpmeta:hasBiblioInfo ?bibinfo}
		|	FILTER(STRSTARTS(str(?doc), "https://meta.icos-cp.eu/"))
		|}""".stripMargin
		// using "${host}" will only work in prod?

		runSparqlQuery(sparqler, docsQuery, "doc")

	end docObjs

	def dataObjs(sparqler: SparqlRunner)(using configs: EnvriConfigs, envri: Envri): Seq[URI] =

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

		runSparqlQuery(sparqler, query, "dobj")
	end dataObjs

	def commonJson(obj: StaticObject, handleProxies: HandleProxiesConfig)(using envri: Envri, conf: EnvriConfig): JsObject =
		val landingPage = JsString(staticObjLandingPage(obj.hash).toString)

		val published: JsValue = asOptJsString(obj.submission.stop.map(_.toString))

		JsObject(
			"@context"              -> JsString("https://schema.org"),
			"url"                   -> landingPage,
			"alternateName"         -> JsString(obj.fileName),
			"datePublished"         -> published,
			"publisher"             -> publisher,
			"inLanguage"            -> JsArray(
				JsObject(
					"@type" -> JsString("Language"),
					"name"  -> JsString("English")
				)
			),
			"acquireLicensePage"    -> obj.references.licence.fold(JsNull)(
				lic => JsString(lic.url.toString)
			),
			"isPartOf"              -> isPartOf(obj),
			"name"                  -> asOptJsString(obj.references.title),
			"identifier"            -> identifier(obj, handleProxies),
			"provider"              -> agentToSchemaOrg(obj.submission.submitter),
		)

	def merge(obj1: JsObject, obj2: JsObject): JsObject = JsObject(obj1.fields ++ obj2.fields)

	def collJson(coll: StaticCollection, handleProxies: HandleProxiesConfig)(using envri: Envri, conf: EnvriConfig) =
		JsObject(
			"@type"                 -> JsString("Collection"),
			"description"           -> description(coll.references, coll.description),
			"abstract"              -> description(coll.references, coll.description),
			"creator"               -> creator(coll.references),
			"contributor"           -> contributor(coll.references)
		)
		// alternatename
		// datepublished
		// inlanguage
		// acquirelicensepage
		// ispartof
		// name
		// identifier
		// provider
		// temporal coverage ? 
		// spatial coverage ?


	def docJson(doc: DocObject, handleProxies: HandleProxiesConfig)(using envri: Envri, conf: EnvriConfig): JsObject =
		val doiLicenses = for
			doi        <- doc.references.doi.toSeq
			rightsList <- doi.rightsList.toSeq
			rights     <- rightsList
			uri        <- rights.rightsUri
		yield JsString(uri)

		val licenceJs = if doiLicenses.isEmpty
			then doc.references.licence.fold(JsNull){lic => JsString(lic.baseLicence.toString)}
			else JsArray(doiLicenses*)

		val id = JsString(doc.doi.fold(doc.pid.fold(staticObjLandingPage(doc.hash).toString)(_.toString))(_.toString))

		merge(commonJson(doc, handleProxies), JsObject(
			"@type"                 -> JsString("DigitalDocument"),
			"@id"                   -> id,
			"license"               -> licenceJs,
			"description"           -> description(doc.references, doc.description),
			"abstract"              -> description(doc.references, doc.description),
			"creator"               -> creator(doc.references),
			"contributor"           -> contributor(doc.references)
		))
	end docJson

	def json(dobj: DataObject, handleProxies: HandleProxiesConfig)(using conf: EnvriConfig, envri: Envri): JsObject =
		val landingPage = JsString(staticObjLandingPage(dobj.hash).toString)

		val description: JsValue =
			val l3Descr = dobj.specificInfo.left.toSeq.flatMap(_.description)
			val specComments = dobj.specification.self.comments
			val prodComment = dobj.production.flatMap(_.comment)
			val citation = dobj.references.citationString
			val allDescrs = (l3Descr ++ specComments ++ prodComment ++ citation).map(_.trim).filter(_.length > 0)
			if(allDescrs.isEmpty) JsNull else JsString(allDescrs.mkString("\n"))

		val modified = JsString(
			(dobj.production.map(_.dateTime).toSeq :+ dobj.submission.start).sorted.head.toString
		)

		val keywords = dobj.keywords.fold(JsArray.empty)(k =>
			JsArray(k.map(JsString(_)).toVector)
		)

		val country: Option[CountryCode] = envri match
			case Envri.SITES => CountryCode.unapply("SE")
			case _ => dobj.specificInfo.toOption.flatMap(
				_.acquisition.station.specificInfo match
					case iss: IcosStationSpecifics => iss.countryCode
					case _ => None
			)

		val spatialCoverage = dobj.coverage.fold[JsValue](JsNull)(f => geoFeatureToSchemaOrgPlace(f, country))

		val temporalCoverage: JsValue = dobj.specificInfo.fold(
			l3 => timeIntToSchemaOrg(l3.temporal.interval),
			_.acquisition.interval.map(timeIntToSchemaOrg).getOrElse(JsNull)
		)

		val stationCreator = dobj.specificInfo.fold(
			_ => JsNull,
			l2 => orgToSchemaOrg(l2.acquisition.station.org, l2.acquisition.station.responsibleOrganization)
		)

		val creator = envri match
			case Envri.SITES => stationCreator
			case _ => dobj.references.authors.toSeq.flatten match
				case Seq() => dobj.production.map(p => agentToSchemaOrg(p.creator)).getOrElse(stationCreator)
				case authors => JsArray(authors.map(agentToSchemaOrg).toVector)

		val producer = dobj.production.map(p => agentToSchemaOrg(p.host.getOrElse(p.creator))).getOrElse(JsNull)

		val contributor = dobj.production.fold[JsValue](JsNull)(p =>
			JsArray(p.contributors.map(agentToSchemaOrg).toVector)
		)

		val distribution= dobj.accessUrl.fold[JsValue](JsNull){_ =>
			val contType = implicitly[ContentTypeResolver].apply(dobj.fileName)
			val accessUrl = s"https://${conf.dataHost}/licence_accept?ids=%5B%22${dobj.hash.base64Url}%22%5D"
			JsObject(
				"contentUrl"     -> JsString(accessUrl),
				"encodingFormat" -> JsString(contType.mediaType.toString),
				"sha256" -> JsString(dobj.hash.hex),
				"contentSize" -> dobj.size.fold[JsValue](JsNull){size => JsString(s"$size B")}
			)
		}

		val variableMeasured = dobj.specificInfo
			.fold(_.variables, _.columns)
			.fold[JsValue](JsNull)(v => JsArray(
					v.map(variable =>
						JsObject(
							"@type"       -> JsString("PropertyValue"),
							"name"        -> JsString(variable.label),
							"description" -> asOptJsString(variable.valueType.self.label),
							"unitText"    -> asOptJsString(variable.valueType.unit)
						)
					).toVector)
			)

		merge(commonJson(dobj, handleProxies), JsObject(
			"@type"                 -> JsString("Dataset"),
			"@id"                   -> landingPage,
			"description"           -> JsString(description.toString.take(5000)),
			"includedInDataCatalog" -> JsObject(
				"@type" -> JsString("DataCatalog"),
				"name"  -> JsString(conf.dataHost)
			),
			"license"               -> dobj.references.licence.flatMap(_.baseLicence).fold(JsNull)(
				lic => JsString(lic.toString)
			),
			"distribution"          -> distribution,
			"dateModified"          -> modified,
			"keywords"              -> keywords,
			"spatialCoverage"       -> spatialCoverage,
			"temporalCoverage"      -> temporalCoverage,
			"producer"              -> producer,
			"creator"               -> creator,
			"contributor"           -> contributor,
			"variableMeasured"      -> variableMeasured,
		))
	end json

	def description(references: References, fallbackDescription: Option[String]) = references.doi match
		case None => fallbackDescription.fold(JsNull)(descr => JsString(descr))
		case Some(doiMeta) => JsString(doiMeta.descriptions.map(_.description).mkString("\n"))

	def creator(references: References) = references.doi match
		case None => references.authors.fold(JsNull)(authors => JsArray(authors.map(agentToSchemaOrg).toVector))
		case Some(doiMeta) => JsArray(doiMeta.creators.map(personToSchemaOrg).toVector)

	def contributor(references: References) = references.doi.fold(JsNull)(dm => JsArray(dm.contributors.map(personToSchemaOrg).toVector))

	def keywords(references: References) = references.doi match
		case None => JsNull
		case Some(dm) => JsArray(dm.subjects.map(s => JsString(s.toString)).toVector)


	def publisherLogo(using envri: Envri): JsValue = envri match
		case Envri.SITES => JsString("https://static.icos-cp.eu/images/sites-logo.png")
		case Envri.ICOS => JsString("https://static.icos-cp.eu/images/ICOS_RI_logo_rgb.png")

	def publisher(using envri: Envri, conf: EnvriConfig) = JsObject(
				"@type" -> JsString("Organization"),
				"@id"   -> JsString(conf.dataHost),
				"name"  -> JsString(s"$envri data portal"),
				"url"   -> JsString(s"https://${conf.dataHost}"),
				"logo"  -> publisherLogo
			)

	def affiliation(affiliation: Affiliation) = JsObject(
			"@type" -> JsString("Organization"),
			"@id"   -> JsString(affiliation.name),
			"name"  -> JsString(affiliation.name)
		)

	def isPartOf(obj: StaticObject) = JsArray(obj.parentCollections.map(coll =>
		JsString(coll.uri.toString)).toVector
	)

	def identifier(obj: StaticObject, handleProxies: HandleProxiesConfig): JsValue =
		val ids: Vector[JsString] = Vector(
			obj.doi -> handleProxies.doi,
			obj.pid -> handleProxies.basic
		).collect{
			case (Some(id), proxy) => JsString(proxy.toString + id)
		}
		ids match
			case Vector() => JsNull
			case Vector(single) => single
			case _ => JsArray(ids)

	def geoFeatureToSchemaOrg(cov: GeoFeature): JsValue = cov match

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

	def personToSchemaOrg(person: Person): JsObject = JsObject(
		"@type"      -> JsString("Person"),
		"@id"        -> getDoiPersonUrl(person).fold(JsNull)(url => JsString(url)),
		"name"       -> JsString(person.name.toString),
		"affiliation"-> JsArray(person.affiliation.map(aff => affiliation(aff)).toVector)
	)

	def agentToSchemaOrg(agent: Agent): JsObject = agent match

		case org: Organization => orgToSchemaOrg(org, None)

		case Person(self, firstName, lastName, _, _) =>
			JsObject(
				"@type"      -> JsString("Person"),
				"@id"        -> JsString(self.uri.toString),
				"sameAs"     -> JsString(self.uri.toString),
				"givenName"  -> JsString(firstName),
				"familyName" -> JsString(lastName),
				"name"       -> JsString(s"$firstName $lastName")
			)

	def timeIntToSchemaOrg(int: TimeInterval) =  JsString(s"${int.start}/${int.stop}")

	def asOptJsString(sOpt: Option[String]): JsValue = sOpt.fold[JsValue](JsNull)(JsString.apply)

	def countryCodeToSchemaOrg(country: Option[CountryCode]) = country.fold[JsValue](JsNull)(country =>
		JsObject(
			"@type"      -> JsString("Country"),
			"identifier" -> JsString(country.code),
			"name"       -> JsString(country.displayCountry)
		)
	)

	def geoFeatureToSchemaOrgPlace(feature: GeoFeature, country: Option[CountryCode] = None): JsValue = feature match
		case FeatureCollection(geoms, _) =>
			JsArray(geoms.map(geo => geoFeatureToSchemaOrgPlace(geo, country)).toVector)
		case _ =>
			JsObject(
				"@type"            -> JsString("Place"),
				"name"             -> asOptJsString(feature.label),
				"geo"              -> geoFeatureToSchemaOrg(feature),
				"containedInPlace" -> countryCodeToSchemaOrg(country),
			)
end SchemaOrg
