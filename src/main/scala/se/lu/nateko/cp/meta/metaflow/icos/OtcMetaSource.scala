package se.lu.nateko.cp.meta.metaflow.icos

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{IRI, Literal, Value, ValueFactory}
import org.eclipse.rdf4j.query.BindingSet
import se.lu.nateko.cp.meta.api.{CustomVocab, SparqlQuery, SparqlRunner, UriId}
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.services.{CpVocab, MetadataException}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.time.Instant

class OtcMetaSource(
	server: WriteNotifyingInstanceServer, sparql: SparqlRunner, val log: LoggingAdapter
) extends TriggeredMetaSource[OTC.type] {

	private type O = OTC.type
	private type OtcId = TcId[O]
	private type OrgMap = Map[IRI, TcPlainOrg[O]]
	private val otcVocab = new OtcMetaVocab(server.factory)

	override def registerListener(actor: ActorRef): Unit = {
		server.setSubscriber(() => actor ! 1)
	}

	override def readState: Validated[State] = for(
		comms <- getComments;
		people <- getPeople;
		otherOrgs <- getCompsAndInsts;
		stations <- getStations(otherOrgs, comms);
		orgs = stations ++ otherOrgs;
		membs <- getMemberships(orgs, people);
		sensorLookup <- getSensorDeployment;
		instruments <- getInstruments(orgs, sensorLookup)
	) yield new TcState(stations.values.toSeq, membs, instruments)

	private def getStations(orgs: OrgMap, comments: Map[IRI, Seq[String]]): Validated[Map[IRI, TcStation[O]]] = {
		val q = """
			|prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
			|select *
			|from <http://meta.icos-cp.eu/resources/otcmeta/>
			|where{
			|	{
			|		select ?st (max(?deplInfo) as ?di) where{
			|			?depl otc:toStation ?st .
			|			optional {?depl otc:hasEndTime ?deplEnd}
			|			bind(if(bound(?deplEnd), concat(str(?deplEnd), str(?depl)), ?depl) as ?deplInfo)
			|		}
			|		group by ?st
			|	}
			|	?st otc:hasStationId ?id ; otc:hasName ?name .
			|	bind(if(isIri(?di), ?di, iri(substr(?di, 11))) as ?depl)
			|	?depl otc:ofPlatform ?plat .
			|	optional {?plat otc:hasLatitude ?lat ; otc:hasLongitude ?lon }
			|	optional {?plat otc:hasSpatialReference ?geoJson }
			|	optional {?plat otc:hasPicture ?picture }
			|	optional {?plat rdfs:seeAlso ?seeAlsoPlat }
			|	optional {?st otc:countryCode ?countryCode }
			|	optional {?st otc:hasStationClass ?stationClass }
			|	optional {?st otc:hasResponsibleOrg ?respOrg }
			|	optional {?st rdfs:seeAlso ?seeAlsoSt }
			|}
		|""".stripMargin

		getLookupV(q, "st"){(b, tcId) =>
			for(
				stUri <- qresValueReq(b, "st").flatMap(ensureIriValue);
				platUri <- qresValueReq(b, "plat").flatMap(ensureIriValue);
				latOpt <- qresValue(b, "lat").flatMap(parseDouble).optional;
				lonOpt <- qresValue(b, "lon").flatMap(parseDouble).optional;
				geoJsonOpt <- qresValue(b, "geoJson").map(_.stringValue).optional;
				statClass <- qresValue(b, "stationClass").map(v => IcosStationClass.valueOf(v.stringValue)).optional;
				ccode <- qresValue(b, "countryCode").flatMap{v =>
					val ccStr = v.stringValue
					CountryCode.unapply(ccStr).fold(Validated.error(s"Bad country code $ccStr")){
						Validated.ok
					}
				}.optional;
				stIdStr <- qresValueReq(b, "id").map(_.stringValue);
				name <- qresValueReq(b, "name").map(_.stringValue);
				posOpt = for(lat <- latOpt; lon <- lonOpt) yield Position(lat, lon, None, Some(s"$name position"), None);
				coverOpt <- (new Validated(geoJsonOpt, Nil)).flatMap{geoJson =>
					Validated.fromTry(GeoJson.toFeature(geoJson)).map(_.withLabel(s"$name geo-coverage"))
				}.optional;
				pictUri <- qresValue(b, "picture").flatMap(parseUriLiteral).optional;
				websitePlat <- qresValue(b, "seeAlsoPlat").flatMap(ensureIriValue).optional;
				websiteSt <- qresValue(b, "seeAlsoSt").flatMap(ensureIriValue).optional;
				respOrg <- qresValue(b, "respOrg").flatMap(ensureIriValue).optional
			) yield{

				TcStation[O](
					cpId = stationId(UriId.escaped(stIdStr)),
					tcId = tcId,
					core = Station(
						org = Organization(
							self = UriResource(
								uri = otcVocab.dummyUri,
								label = Some(stIdStr),
								comments = comments.getOrElse(stUri, Nil) ++ comments.getOrElse(platUri, Nil)
							),
							name = name,
							email = None,
							website = websiteSt.orElse(websitePlat).map(_.toJava),
							webpageDetails = None
						),
						id = stIdStr,
						location = posOpt,
						coverage = coverOpt,
						responsibleOrganization = None,
						pictures = pictUri.toSeq,
						countryCode = ccode,
						specificInfo = OtcStationSpecifics(None, statClass, None, false, None, Seq.empty),
						funding = None
					),
					responsibleOrg = respOrg.flatMap(orgs.get),
					funding = Nil
				)
			}
		}.map(_.toMap)
	}

	private def getCompsAndInsts: Validated[Map[IRI, TcGenericOrg[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct ?org ?name ?label where{
		|	values ?orgClass {otc:CommercialCompany otc:AcademicInstitution}
		|	?org a ?orgClass .
		|	?org otc:hasName ?name .
		|	optional{?org rdfs:label ?label }
		|}""".stripMargin

		getLookup(q, "org"){(b, tcId) => TcGenericOrg(
			cpId = UriId(tcId.id),
			tcIdOpt = Some(tcId),
			org = Organization(
				self = UriResource(EtcMetaSource.dummyUri, Option(b.getValue("label")).map(_.stringValue), Nil),
				name = b.getValue("name").stringValue,
				email = None,
				website = None,
				webpageDetails = None
			)
		)}.map(_.toMap)
	}

	private def getPeople: Validated[Map[IRI, TcPerson[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select distinct * where{
		|	?p a otc:Person .
		|	?p otc:hasFirstName ?fname .
		|	?p otc:hasLastName ?lname .
		|	optional{?p otc:hasEmail ?email }
		|	optional{?p otc:hasOrcidId ?orcid }
		|}""".stripMargin

		getLookup(q, "p"){(b, tcId) =>
			val fname = b.getValue("fname").stringValue
			val lname = b.getValue("lname").stringValue
			TcPerson(
				cpId = CpVocab.getPersonCpId(fname, lname),
				tcIdOpt = Some(tcId),
				fname = fname,
				lname = lname,
				email = Option(b.getValue("email")).map(_.stringValue.toLowerCase),
				orcid = Option(b.getValue("orcid")).flatMap(v => Orcid.unapply(v.stringValue))
			)
		}.map(_.toMap)
	}

	private def getComments: Validated[Map[IRI, Seq[String]]] = {
		val q = """select *
			|from <http://meta.icos-cp.eu/resources/otcmeta/>
			|where{ ?iri rdfs:comment ?comm }
		|""".stripMargin
		getLookupV(q, "iri"){(b, _) =>
			qresValueReq(b, "comm").map(_.stringValue)
		}.map(_.groupMap(_._1)(_._2))
	}

	private def getLookup[T](query: String, entVar: String)(maker: (BindingSet, TcId[O]) => T): Validated[IndexedSeq[(IRI, T)]] = {
		getLookupV(query, entVar)((bs, id) => Validated(maker(bs, id)))
	}

	private def getLookupV[T](query: String, entVar: String)(maker: (BindingSet, TcId[O]) => Validated[T]): Validated[IndexedSeq[(IRI, T)]] = {
		Validated(sparql.evaluateTupleQuery(SparqlQuery(query))).flatMap{iter =>
			val entValids = iter.toIndexedSeq.map{b =>
				for(
					entIri <- qresValueReq(b, entVar)
						.collect{case iri: IRI => iri}
						.require(s"Expected $entVar to be an IRI, got something else");
					ent <- maker(b, TcConf.makeId[O](entIri.getLocalName))
				) yield entIri -> ent
			}
			Validated.sequence(entValids)
		}
	}

	private def qresValue(bs: BindingSet, vname: String): Validated[Value] =
		new Validated(Option(bs.getValue(vname)))

	private def qresValueReq(bs: BindingSet, vname: String): Validated[Value] =
		qresValue(bs, vname).require(s"Variable $vname absent in SPARQL query results")

	private def getMemberships(orgs: Map[IRI, TcOrg[O]], pers: Map[IRI, TcPerson[O]]): Validated[Seq[Membership[O]]] = {
		val q = """prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
		|select * where{
		|	?role a otc:AssumedRole .
		|	?role otc:hasRole ?roleKind .
		|	?role otc:atOrganization ?org .
		|	?role otc:hasHolder ?person .
		|	optional{?role otc:hasAttributionWeight ?weight}
		|	optional{?role otc:hasStartTime ?start}
		|	optional{?role otc:hasEndTime ?end}
		|}""".stripMargin

		getLookup(q, "role"){(b, tcId) =>
			val orgIri = b.getValue("org").asInstanceOf[IRI]
			inline def orgError = s"Organization $orgIri not found, maybe it's a station without a platform deployment"
			val role = new AssumedRole[O](
				kind = otcVocab.Roles.map(b.getValue("roleKind").asInstanceOf[IRI]),
				holder = pers(b.getValue("person").asInstanceOf[IRI]),
				org = orgs.get(orgIri).getOrElse(throw MetadataException(orgError)),
				weight = Option(b.getValue("weight")).map(_.stringValue.toInt),
				extra = None
			)
			Membership[O](
				cpId = UriId(tcId.id),
				role = role,
				start = parseDate(b.getValue("start")),
				stop = parseDate(b.getValue("end"))
			)
		}.map(_.map(_._2))
	}

	private def getInstruments(
		orgLookup: Map[IRI, TcOrg[O]],
		sensorLookup: Map[OtcId, Seq[UriId]]
	): Validated[IndexedSeq[TcInstrument[O]]] = {
		val q = """
			|prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
			|select ?instr ?model ?serNum ?name ?vendor where{
			|	values ?kind {otc:Instrument otc:Sensor}
			|	?instr a ?kind ; otc:device ?device .
			|	?device otc:hasModelName ?model ; otc:hasManufacturer ?vendor .
			|	optional{ ?instr otc:hasSerialNumber ?serNum }
			|	optional{ ?instr otc:hasName ?name }
			|}
		|""".stripMargin
		getLookupV(q, "instr"){(b, tcId) =>
			for(
				model <- qresValue(b, "model").flatMap(parseString).orElse(TcMetaSource.defaultInstrModel);
				sn <- qresValue(b, "serNum").flatMap(parseString).orElse(TcMetaSource.defaultSerialNum);
				nameOpt <- qresValue(b, "name").flatMap(parseString).optional;
				vendorOpt <- qresValue(b, "vendor").collect{case iri: IRI => iri}.optional
			) yield
				TcInstrument[O](
					tcId = tcId,
					model = model,
					sn = sn,
					name = nameOpt,
					partsCpIds = sensorLookup.getOrElse(tcId, Nil),
					vendor = vendorOpt.flatMap(orgLookup.get),
					deployments = Nil
				)
		}.map(_.map(_._2))
	}

	private def getSensorDeployment: Validated[Map[OtcId, Seq[UriId]]] = {
		val q = """|
			prefix otc: <http://meta.icos-cp.eu/ontologies/otcmeta/>
			select distinct ?instr ?sensor where{
				?depl otc:toInstrument ?instr ; otc:ofSensor ?sensor .
				filter not exists {?depl otc:hasEndTime ?deplEnd}
			}
		|""".stripMargin
		getLookupV(q, "instr"){(b, instrId) =>
			qresValueReq(b, "sensor")
				.collect{case iri: IRI => instrId -> TcConf.tcScopedId[O](UriId(iri))}
				.require(s"sensor not found for instrument $instrId")
		}.map(_.groupMap(_._2._1)(_._2._2))
	}

	private def parseDate(v: Value): Option[Instant] = v match {
		case lit: Literal if lit.getDatatype == XSD.DATE =>
			Some(Instant.parse(lit.stringValue + "T12:00:00Z"))
		case _ => None
	}

	private def parseLiteral(v: Value, dtype: IRI): Validated[String] = v match {
		case lit: Literal if lit.getDatatype === dtype =>
			Validated.ok(lit).map(_.stringValue.trim)
		case _ =>
			Validated.error(s"Expected $v to be a RDF literal of type $dtype")
	}

	private def parseDouble(v: Value) = parseLiteral(v, XSD.DOUBLE).map(_.toDouble)
	private def parseString(v: Value) = parseLiteral(v, XSD.STRING)
	private def parseUriLiteral(v: Value) = parseLiteral(v, XSD.ANYURI).map(new java.net.URI(_))
	private def ensureIriValue(v: Value): Validated[IRI] = v match{
		case iri: IRI => Validated.ok(iri)
		case _ => Validated.error(s"expected an IRI value, got $v")
	}

}

class OtcMetaVocab(val factory: ValueFactory) extends CustomVocab{

	given bup: BaseUriProvider = makeUriProvider("http://meta.icos-cp.eu/ontologies/otcmeta/")

	val dummyUri = new java.net.URI(bup.baseUri + "dummy")

	// val hasHolder = getRelativeRaw("hasHolder")
	// val hasRole = getRelativeRaw("hasRole")
	// val atOrganization = getRelativeRaw("atOrganization")

	// val spatialReference = getRelativeRaw("hasSpatialReference")
	// val hasStartTime = getRelativeRaw("hasStartTime")
	// val hasEndTime = getRelativeRaw("hasEndTime")

	// val assumedRoleClass = getRelativeRaw("AssumedRole")

	object Roles{
		val dataSubmitter = getRelativeRaw("dataSubmitter")
		val engineer = getRelativeRaw("engineer")
		val pi = getRelativeRaw("pi")
		val researcher = getRelativeRaw("researcher")

		val map: Map[IRI, Role] = Map(
			dataSubmitter -> DataManager,
			engineer -> Engineer,
			pi -> PI,
			researcher -> Researcher
		)
	}
}

