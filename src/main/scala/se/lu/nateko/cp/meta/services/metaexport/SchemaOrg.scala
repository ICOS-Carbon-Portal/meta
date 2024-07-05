package se.lu.nateko.cp.meta.services.metaexport

import akka.http.scaladsl.server.directives.ContentTypeResolver
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.doi.{meta => doi}
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.HandleProxiesConfig
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.utils.json.*
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.views.LandingPageHelpers.doiAgentUri
import spray.json.*
import se.lu.nateko.cp.meta.utils.*

import java.net.URI

import doi.DescriptionType.{Abstract => DoiAbstract}
import java.time.LocalDate
import java.time.ZoneOffset
import se.lu.nateko.cp.doi.meta.PersonalName
import se.lu.nateko.cp.doi.meta.GenericName
import eu.icoscp.envri.Envri

object SchemaOrg:

	def asOptJsString(sOpt: Option[String]): JsValue = optJs(sOpt)(JsString.apply)

	def asOptArray[T](elems: Iterable[T])(toJs: T => JsValue): JsValue = elems.map(toJs).toVector match
		case Vector() => JsNull
		case Vector(single) => single
		case many => JsArray(many)

	def asOptArray[T](elemsOpt: Option[Iterable[T]])(toJs: T => JsValue): JsValue = optJs(elemsOpt)(asOptArray(_)(toJs))

	def optJs[T](opt: Option[T])(toJs: T => JsValue): JsValue = opt.fold(JsNull)(toJs)

	def sparqlUriSeq(sparqler: SparqlRunner, query: String, varName: String): Seq[URI] = sparqler
		.evaluateTupleQuery(SparqlQuery(query))
		.map(_.getValue(varName))
		.collect{case iri: IRI => iri.toJava}
		.toIndexedSeq

	def collObjs(sparqler: SparqlRunner)(using envriConf: EnvriConfig): Seq[URI] =

		val collsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?coll where{
		|	?coll a cpmeta:Collection .
		|	?coll cpmeta:hasDoi ?doi .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?coll}
		|	FILTER(STRSTARTS(str(?coll), "${envriConf.dataItemPrefix}"))
		|}""".stripMargin

		sparqlUriSeq(sparqler, collsQuery, "coll")

	def docObjs(sparqler: SparqlRunner)(using envriConf: EnvriConfig): Seq[URI] =

		val docsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?doc where{
		|	?doc a cpmeta:DocumentObject .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?doc}
		|	FILTER(STRSTARTS(str(?doc), "${envriConf.dataItemPrefix}"))
		|}""".stripMargin

		sparqlUriSeq(sparqler, docsQuery, "doc")

	end docObjs

	def dataObjs(sparqler: SparqlRunner)(using envriConf: EnvriConfig): Seq[URI] =

		val specsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?spec
		|where{
		|	VALUES ?level { 2 3 }
		|	?spec cpmeta:hasDataLevel ?level .
		|	FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
		|	FILTER(STRSTARTS(str(?spec), "${envriConf.metaItemPrefix}"))
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

		sparqlUriSeq(sparqler, query, "dobj")
	end dataObjs
end SchemaOrg


class SchemaOrg(handleProxies: HandleProxiesConfig)(using envri: Envri, envriConf: EnvriConfig):
	import SchemaOrg.*

	def collJson(coll: StaticCollection): JsObject =
		val hasPart = asOptArray(coll.members){item =>
			val name = item match
				case obj: PlainStaticObject => obj.name
				case coll: StaticCollection => coll.title
			val landingPage = JsString(item.res.toString)
			JsObject(
				"@id"   -> landingPage,
				"url"   -> landingPage,
				"name"  -> JsString(name)
			)
		}
		val isBasedOn = optJs(coll.previousVersion)(fromPreviousVersion)
		merge(
			commonJson(coll),
			docCollJson(coll),
			JsObject(
				"@type"     -> JsString("Collection"),
				"hasPart"   -> hasPart,
				"isBasedOn" -> isBasedOn
			)
		)

	def docJson(doc: DocObject): JsObject = merge(
		docCollJson(doc),
		objCommonJson(doc),
		JsObject("@type" -> JsString("DigitalDocument"))
	)

	def dataJson(dobj: DataObject): JsObject =

		val description: JsValue =
			val l3Descr = dobj.specificInfo.left.toSeq.flatMap(_.description)
			val specComments = dobj.specification.self.comments
			val prodComment = dobj.production.flatMap(_.comment)
			val citation = dobj.references.citationString
			val allDescrs = (l3Descr ++ specComments ++ prodComment ++ citation).map(_.trim).filter(_.length > 0)
			if allDescrs.isEmpty then JsNull else
				JsString(truncateDescription(allDescrs.mkString("\n")))

		val modified = JsString(
			(dobj.production.map(_.dateTime).toSeq :+ dobj.submission.start).sorted.head.toString
		)

		val keywords = asOptArray(dobj.keywords)(JsString.apply)

		val country: Option[CountryCode] = envri match
			case Envri.SITES => CountryCode.unapply("SE")
			case Envri.ICOS | Envri.ICOSCities => dobj.specificInfo.toOption.flatMap(_.acquisition.station.countryCode)

		val spatialCoverage: JsValue = optJs(dobj.coverage)(fromGeoFeature(_, country))

		val temporalCoverage: JsValue = dobj.specificInfo.fold(
			l3 => fromTimeInt(l3.temporal.interval),
			l2 => optJs(l2.acquisition.interval)(fromTimeInt)
		)

		val stationCreator = optJs(dobj.specificInfo.toOption)(
			l2 => fromOrganization(l2.acquisition.station.org, l2.acquisition.station.responsibleOrganization)
		)

		val creator = envri match
			case Envri.SITES => stationCreator
			case Envri.ICOS | Envri.ICOSCities => dobj.references.authors.toSeq.flatten match
				case Seq() => dobj.production.map(p => fromAgent(p.creator)).getOrElse(stationCreator)
				case authors => asOptArray(authors)(fromAgent)

		val producer = dobj.production.map(p => fromAgent(p.host.getOrElse(p.creator))).getOrElse(JsNull)

		val contributor = asOptArray(dobj.production.map(_.contributors))(fromAgent)

		val distribution= optJs(dobj.accessUrl){_ =>
			val contType = implicitly[ContentTypeResolver].apply(dobj.fileName)
			val accessUrl = s"https://${envriConf.dataHost}/licence_accept?ids=%5B%22${dobj.hash.base64Url}%22%5D"
			JsObject(
				"contentUrl"     -> JsString(accessUrl),
				"encodingFormat" -> JsString(contType.mediaType.toString),
				"sha256"         -> JsString(dobj.hash.hex),
				"contentSize"    -> optJs(dobj.size)(size => JsString(s"$size B"))
			)
		}

		val variableMeasured = asOptArray(dobj.specificInfo.fold(_.variables, _.columns))(
			variable => JsObject(
				"@type"       -> JsString("PropertyValue"),
				"name"        -> JsString(variable.label),
				"description" -> asOptJsString(variable.valueType.self.label),
				"unitText"    -> asOptJsString(variable.valueType.unit)
			)
		)

		objCommonJson(dobj) ++ JsObject(
			"@type"                 -> JsString("Dataset"),
			"description"           -> description,
			"includedInDataCatalog" -> JsObject(
				"@type" -> JsString("DataCatalog"),
				"name"  -> JsString(envriConf.dataHost)
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
		)
	end dataJson

	private def commonJson(item: CitableItem) =
		import item.references

		val landingPageUri = item match
			case obj: StaticObject => staticObjLandingPage(obj.hash)
			case coll: StaticCollection => staticCollLandingPage(coll.hash)

		val landingPage = JsString(landingPageUri.toString)

		val licenceJs = references.licence
			.map(lic => JsString(lic.url.toString))
			.getOrElse{
				val doiLicUris = for
					doi        <- references.doi.toSeq
					rightsList <- doi.rightsList.toSeq
					rights     <- rightsList
					uri        <- rights.rightsUri
				yield uri

				asOptArray(doiLicUris)(JsString.apply)
			}

		//TODO Move to EnvriConfig
		val publisherLogoUri: String = envri match
			case Envri.SITES => "https://static.icos-cp.eu/images/sites-logo.png"
			case Envri.ICOS | Envri.ICOSCities => "https://static.icos-cp.eu/images/ICOS_RI_logo_rgb.png"

		JsObject(
			"@context"              -> JsString("https://schema.org"),
			"@id"                   -> landingPage,
			"url"                   -> landingPage,
			"name"                  -> asOptJsString(item.references.title),
			"identifier"            -> identifier(item),
			"inLanguage"            -> JsObject(
				"@type" -> JsString("Language"),
				"name"  -> JsString("English")
			),
			"publisher"             -> JsObject(
				"@type" -> JsString("Organization"),
				"@id"   -> JsString(envriConf.dataHost),
				"name"  -> JsString(s"$envri data portal"),
				"url"   -> JsString(s"https://${envriConf.dataHost}"),
				"logo"  -> JsString(publisherLogoUri)
			),
			"license"               -> licenceJs,
			"acquireLicensePage"    -> asOptJsString(references.licence.map(_.url.toString)),
		)
	end commonJson

	private def docCollJson(item: StaticCollection | DocObject): JsObject =
		import item.{references => refs}

		val description: JsObject =
			val doiDescrs = refs.doi.toSeq.flatMap(_.descriptions)
			val (abstracts, otherDescrs) = doiDescrs.partition(_.descriptionType == DoiAbstract)
			val abstractTexts = abstracts.map(_.description)
			val otherTexts =
				if doiDescrs.nonEmpty
				then otherDescrs.map(_.description)
				else item match
					case coll: StaticCollection => coll.description.toSeq
					case doc: DocObject => doc.description.toSeq
			val jsFields = (abstractTexts.map("abstract" -> _) ++ otherTexts.map("description" -> _)).map(
				(field, text) => field -> JsString(truncateDescription(text))
			)
			JsObject(jsFields*)

		val keywords: JsValue =
			val words = (refs.keywords ++ refs.doi.map(_.subjects.map(_.subject))).flatten
			asOptArray(words.toVector.distinct.sorted)(JsString.apply)

		val creator = refs.doi
			.map(doiMeta => asOptArray(doiMeta.creators)(fromDoiAgent))
			.filter(_ != JsNull)
			.getOrElse(
				asOptArray(refs.authors)(fromAgent)
			)

		val contributor = asOptArray(refs.doi.map(_.contributors))(fromDoiAgent)

		description ++ JsObject(
			"keywords"              -> keywords,
			"creator"               -> creator,
			"contributor"           -> contributor
		)

	private def objCommonJson(obj: StaticObject): JsObject =
		val partOf = asOptArray(obj.parentCollections)(coll => JsString(coll.uri.toString))
		val basedOn = asOptArray(obj.previousVersion.flattenToSeq)(fromPreviousVersion)
		val status =
			if obj.size.isEmpty then "Incomplete"
			else if obj.nextVersion.nonEmpty then "Deprecated"
			else "Published"
		JsObject(
			"alternateName"         -> JsString(obj.fileName),
			"datePublished"         -> asOptJsString(obj.submission.stop.map(LocalDate.ofInstant(_, ZoneOffset.UTC).toString)),
			"isPartOf"              -> partOf,
			"provider"              -> fromAgent(obj.submission.submitter),
			"isBasedOn"             -> basedOn,
			"creativeWorkStatus"    -> JsString(status)
		) ++ commonJson(obj)

	private def fromPreviousVersion(url: URI) = JsObject(
		"@type" -> JsString("CreativeWork"),
		"name"  -> JsString("Previous version"),
		"url"   -> JsString(url.toString)
	)

	private def truncateDescription(descr: String): String =
		if descr.size <= 5000 then descr else descr.take(4997) + "..."

	private def identifier(item: CitableItem): JsValue =
		val pid: Option[String] = item match
			case obj: StaticObject => obj.pid
			case _: StaticCollection => None

		val ids = Iterable(
			item.doi -> handleProxies.doi,
			pid -> handleProxies.basic
		).collect{
			case (Some(id), proxy) => s"$proxy$id"
		}
		asOptArray(ids)(JsString.apply)

	private def fromGeoFeature(cov: GeoFeature): JsValue = cov match

		case FeatureCollection(feats, _, _) => JsArray(
			feats.map(fromGeoFeature).toVector
		)

		case Position(lat, lon, altOpt, _, _) => JsObject(
			Map(
				"@type"     -> JsString("GeoCoordinates"),
				"latitude"  -> JsNumber(lat),
				"longitude" -> JsNumber(lon)
			) ++ altOpt.map{alt =>
				"elevation" -> JsNumber(alt)
			}
		)

		case Circle(center, radius, _, _) => JsObject(
			"@type"       -> JsString("GeoCircle"),
			"geoMidpoint" -> fromGeoFeature(center),
			"geoRadius"   -> JsNumber(radius)
		)

		case LatLonBox(min, max, _, _) => JsObject(
			"@type"     -> JsString("GeoShape"),
			"polygon"   -> {
				val (minlat, minlon, maxlat, maxlon) = (min.lat6, min.lon6, max.lat6, max.lon6)
				JsString(s"$minlat $minlon $maxlat $minlon $maxlat $maxlon $minlat $maxlon $minlat $minlon")
			}
		)

		case GeoTrack(points, _, _) => JsObject(
			"@type"     -> JsString("GeoShape"),
			"polygon"   -> JsString(points.map(p => s"${p.lat6} ${p.lon6}").mkString(" "))
		)

		case Polygon(vertices, _, _) => JsObject(
			"@type"     -> JsString("GeoShape"),
			"polygon"   -> JsString(vertices.map(p => s"${p.lat6} ${p.lon6}").mkString(" "))
		)

		case Pin(position, kind) => fromGeoFeature(position)


	private def fromOrganization(org: Organization, parent: Option[Organization]) = JsObject(
		Map(
			"@type"  -> JsString("Organization"),
			"@id"    -> JsString(org.self.uri.toString),
			"name"   -> JsString(org.name),
			"email"  -> asOptJsString(org.email),
		) ++ parent.map{ parent =>
			"parentOrganization" -> JsString(parent.name)
		}
	)

	private def fromDoiAgent(agent: doi.Person): JsObject =
		val agentType = agent.name match
			case _: PersonalName => JsString("Person")
			case _: GenericName  => JsString("Organization")

		JsObject(
			"@type"      -> agentType,
			"@id"        -> asOptJsString(doiAgentUri(agent)),
			"name"       -> JsString(agent.name.toString),
			"affiliation"-> asOptArray(agent.affiliation)(fromDoiAff)
		)

	private def fromDoiAff(affiliation: doi.Affiliation) = JsObject(
		"@type" -> JsString("Organization"),
		"name"  -> JsString(affiliation.name)
	)

	private def fromAgent(agent: Agent): JsObject = agent match

		case org: Organization => fromOrganization(org, None)

		case Person(self, firstName, lastName, _, _) =>
			JsObject(
				"@type"      -> JsString("Person"),
				"@id"        -> JsString(self.uri.toString),
				"sameAs"     -> JsString(self.uri.toString),
				"givenName"  -> JsString(firstName),
				"familyName" -> JsString(lastName),
				"name"       -> JsString(s"$firstName $lastName")
			)

	private def fromTimeInt(int: TimeInterval) =  JsString(s"${int.start}/${int.stop}")

	private def fromCountryCode(country: CountryCode) = JsObject(
		"@type"      -> JsString("Country"),
		"identifier" -> JsString(country.code),
		"name"       -> JsString(country.displayCountry)
	)

	private def fromGeoFeature(feature: GeoFeature, country: Option[CountryCode] = None): JsValue = feature match
		case FeatureCollection(geoms, _, _) =>
			JsArray(geoms.map(geo => fromGeoFeature(geo, country)).toVector)
		case _ =>
			JsObject(
				"@type"            -> JsString("Place"),
				"name"             -> asOptJsString(feature.label),
				"geo"              -> fromGeoFeature(feature),
				"containedInPlace" -> optJs(country)(fromCountryCode),
			)
end SchemaOrg
