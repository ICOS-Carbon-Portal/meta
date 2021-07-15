package se.lu.nateko.cp.meta.icos

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import akka.stream.ActorAttributes
import akka.stream.Materializer
import akka.stream.Supervision
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.meta.EtcConfig
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.ingestion.badm.Badm
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry
import se.lu.nateko.cp.meta.ingestion.badm.BadmLocalDate
import se.lu.nateko.cp.meta.ingestion.badm.BadmValue
import se.lu.nateko.cp.meta.ingestion.badm.EtcEntriesFetcher
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import se.lu.nateko.cp.meta.ingestion.badm.BadmLocalDateTime
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.etcupload.DataType
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.upload.etc._
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.urlEncode
import se.lu.nateko.cp.meta.utils.rdf4j._
import java.net.URI

class EtcMetaSource(conf: EtcConfig, vocab: CpVocab)(implicit system: ActorSystem, mat: Materializer) extends TcMetaSource[ETC.type] {
	import EtcMetaSource._
	import system.dispatcher

	private val baseEtcApiUrl = Uri(conf.metaService.toString)

	def state: Source[State, Cancellable] = Source
		.tick(35.seconds, 5.hours, NotUsed)
		.mapAsync(1){_ =>
			fetchFromEtc().andThen{
				case Failure(err) =>
					system.log.error(err, "ETC metadata fetching/parsing error")
			}
		}
		.withAttributes(ActorAttributes.supervisionStrategy(_ => Supervision.Resume))
		.mapConcat{validated =>
			if(!validated.errors.isEmpty) system.log.warning("ETC metadata problem(s): " + validated.errors.mkString("\n"))
			validated.result.toList
		}


	def fetchFromEtc(): Future[Validated[State]] = {
		val peopleFut = fetchFromTsv(Types.people, getPerson(_))
		val instrumentsFut = fetchFromTsv(Types.instruments, getInstrument(_))

		val futfutValVal = for(
			peopleVal <- peopleFut;
			stationsVal <- fetchStations();
			instrumentsVal <- instrumentsFut
		) yield Validated.liftFuture{
			for(
				people <- peopleVal;
				stations <- stationsVal;
				instruments <- instrumentsVal
			) yield {
				val membExtractor: Lookup => Validated[EtcMembership] = getMembership(
					people.flatMap(p => p.tcIdOpt.map(_ -> p)).toMap,
					stations.map(s => s.tcId -> s).toMap
				)(_)
				fetchFromTsv(Types.roles, membExtractor).map(_.map{membs =>
					//TODO Consider that after mapping to CP roles, a person may (in theory) have duplicate roles at the same station
					new TcState(stations, membs, instruments)
				})
			}
		}

		futfutValVal.flatten.map(_.flatMap(identity))
	}

	def fetchStations(): Future[Validated[Seq[TcStation[ETC.type]]]] = {
		for(
			fundLookupV <- fetchAndParseTsv(Types.funding).map{lookups =>
				for(
					fundersLookup <- parseFunders(vocab, lookups);
					fundings <- parseFundings(lookups, fundersLookup, vocab)
				) yield fundings
			};
			stations <- fetchFromTsv(Types.stations, getStation(fundLookupV)(_))
		) yield stations
	}

	def getFileMeta: Future[Validated[EtcFileMetadataStore]] = {
		val utcFut = fetchFromTsv(Types.stations, getSiteUtc(_))

		val fileMetaFut = utcFut.flatMap{utcInfo =>
			val idLookup = utcInfo.map(_.map{
				case (tcId, id, _) => tcId -> id
			}.toMap)
			fetchFromTsv[(EtcFileMetaKey, EtcFileMeta)](Types.files, getSingleFileMeta(idLookup)(_))
		}

		for(utcV <- utcFut; fileMetaV <- fileMetaFut) yield
			for(utc <- utcV; fileMeta <- fileMetaV) yield {
				val utcMap = utc.collect{
					case (_, id, Some(utcOffset)) => id -> utcOffset
				}.toMap
				val idLookup = utc.map{case (tc, id, _) => id -> tc}.toMap
				new TsvBasedEtcFileMetadataStore(utcMap, fileMeta.toMap, idLookup)
			}
	}

	private def fetchAndParseTsv[T](tableType: String): Future[Seq[Lookup]] = Http()
		.singleRequest(HttpRequest(
			uri = baseEtcApiUrl.withQuery(Uri.Query("type" -> tableType))
		))
		.flatMap(_.entity.toStrict(3.seconds))
		.map(ent => parseTsv(ent.data))

	private def fetchFromTsv[T](tableType: String, extractor: Lookup => Validated[T]): Future[Validated[Seq[T]]] = 
		fetchAndParseTsv(tableType).map(lookups => Validated.sequence(lookups.map(extractor)))

}

object EtcMetaSource{

	type Lookup = Map[String, String]
	type E = ETC.type
	type EtcInstrument = TcInstrument[E]
	type EtcPerson = TcPerson[E]
	type EtcStation = TcStation[E]
	type EtcMembership = Membership[E]
	implicit val envri = Envri.ICOS


	def makeId(id: String): TcId[E] = TcConf.EtcConf.makeId(id)

	object Types{
		val roles = "teamrole"
		val people = "team"
		val stations = "station"
		val instruments = "logger"
		val files = "file"
		val funding = "funding"
	}

	object Vars{
		val lat = "LOCATION_LAT"
		val lon = "LOCATION_LONG"
		val elev = "LOCATION_ELEV"
		val fname = "TEAM_MEMBER_FIRSTNAME"
		val lname = "TEAM_MEMBER_LASTNAME"
		val email = "TEAM_MEMBER_EMAIL"
		val orcid = "TEAM_MEMBER_ORCID"
		val role = "TEAM_MEMBER_ROLE"
		val roleStart = "TEAM_MEMBER_WORKSTART"
		val roleEnd = "TEAM_MEMBER_WORKEND"
		val authorOrder = "TEAM_MEMBER_AUTHORDER"
		val persId = "ID_TEAM"
		val stationTcId = "ID_STATION"
		val siteName = "SITE_NAME"
		val siteId = "SITE_ID"
		val stationClass = "CLASS_ICOS"
		val descr = "SITE_DESC"
		val pictureUrl = "URL_PICTURE"
		val utcOffset = "UTC_OFFSET"
		val annualTemp = "MAT"
		val annualPrecip = "MAP"
		val annualRad = "MAR"
		val climateZone = "CLIMATE_KOPPEN"
		val ecosystemIGBP = "IGBP"
		val stationDocDois = "REFERENCE_DOI_D"
		val stationDataPubDois = "REFERENCE_DOI_P"
		val timeZoneOffset = "UTC_OFFSET"
		val loggerModel = "LOGGER_MODEL"
		val loggerSerial = "LOGGER_SN"
		val loggerId = "LOGGER_ID"
		val fileId = "FILE_ID"
		val fileLoggerId = "FILE_LOGGER_ID"
		val fileFormat = "FILE_FORMAT"
		val fileType = "FILE_TYPE"
		val fundingOrgName = "FUNDING_ORGANIZATION"
		val fundingAwardNumber = "FUNDING_GRANT"
		val fundingAwardUri = "FUNDING_GRANT_URL"
		val fundingAwardTitle = "FUNDING_TITLE"
		val fundingStart = "FUNDING_DATE_START"
		val fundingEnd = "FUNDING_DATE_END"
		val fundingComment = "FUNDING_COMMENT"
	}

	val rolesLookup: Map[String, Option[Role]] = Map(
		"PI"         -> Some(PI),
		"CO-PI"      -> Some(Researcher),
		"MANAGER"    -> Some(Administrator),
		"SCI"        -> Some(Researcher),
		"SCI-FLX"    -> Some(Researcher),
		"SCI-ANC"    -> Some(Researcher),
		"TEC"        -> Some(Engineer),
		"TEC-FLX"    -> Some(Engineer),
		"TEC-ANC"    -> Some(Engineer),
		"DATA"       -> Some(DataManager),
		"ADMIN"      -> None,
		"AFFILIATED" -> None
	)

	def lookUp(varName: String)(implicit lookup: Lookup): Validated[String] =
		new Validated(lookup.get(varName).filter(_.length > 0))

	def lookUpOrcid(varName: String)(implicit lookup: Lookup): Validated[Option[Orcid]] =
		lookUp(varName).optional.flatMap{
			case Some(Orcid(orc)) => Validated.ok(Some(orc))
			case None => Validated.ok(None)
			case Some(badOrcid) => new Validated(None, Seq(s"Could not parse Orcid id from string $badOrcid"))
		}

	def getNumber(varName: String)(implicit lookup: Lookup): Validated[Number] = lookUp(varName).flatMap{
		str => Validated(Badm.numParser.parse(str)).require(s"$varName must have been a number (was $str)")
	}

	private def getLocalDate(varName: String)(implicit lookup: Lookup): Validated[LocalDate] = lookUp(varName).flatMap{
		//case Badm.Date(BadmLocalDateTime(dt)) => Validated.ok(toCET(dt))
		case Badm.Date(BadmLocalDate(date)) => Validated.ok(date)
		case bv => Validated.error(s"$varName must have been a BADM-format local date (was $bv)")
	}

	def getPosition(implicit lookup: Lookup): Validated[Option[Position]] =
		for(
			latOpt <- getNumber(Vars.lat).optional;
			lonOpt <- getNumber(Vars.lon).optional;
			alt <- getNumber(Vars.elev).optional
		) yield for(lat <- latOpt; lon <- lonOpt)
			yield Position(lat.doubleValue, lon.doubleValue, alt.map(_.floatValue), None)

	def getPerson(implicit lookup: Lookup): Validated[EtcPerson] =
		for(
			fname <- lookUp(Vars.fname).require("person must have first name");
			lname <- lookUp(Vars.lname).require("person must have last name");
			tcId <- lookUp(Vars.persId).require("unique ETC's id is required for a person");
			email <- lookUp(Vars.email).optional;
			orcid <- lookUpOrcid(Vars.orcid);
			cpId = CpVocab.getPersonCpId(fname, lname)
		) yield
			TcPerson(cpId, Some(makeId(tcId)), fname, lname, email.map(_.toLowerCase), orcid)

	def getCountryCode(stId: StationId): Validated[CountryCode] = getCountryCode(stId.id.take(2))

	def getCountryCode(s: String): Validated[CountryCode] = s match{
		case CountryCode(cc) => Validated.ok(cc)
		case _ => Validated.error(s + " is not a valid country code")
	}

	private def getSiteUtc(implicit lookup: Lookup): Validated[(Int, StationId, Option[Int])] =
		for(
			techId <- lookUp(Vars.stationTcId).map(_.toInt);
			stationId <- lookUp(Vars.siteId).map{case StationId(sId) => sId};
			utc <- lookUp(Vars.utcOffset).map(_.toInt).optional
		) yield (techId, stationId, utc)

	def parseFunders(vocab: CpVocab, lookups: Seq[Lookup]): Validated[Map[String, TcFunder[ETC.type]]] = Validated
		.sequence(lookups.map(lookUp(Vars.fundingOrgName)(_)))
		.map(
			_.distinct.map{funderName =>
				val cpId = UriId.escaped(funderName)
				val org = Organization(
					self = UriResource(vocab.getOrganization(cpId).toJava, None, Nil),
					name = funderName,
					email = None,
					website = None
				)
				funderName -> TcFunder[ETC.type](
					cpId = cpId,
					tcIdOpt = Some(makeId(s"fund_${funderName.hashCode}")),
					core = Funder(org, None)
				)
			}.toMap
		)

	def parseFundings(
		lookups: Seq[Lookup], funders: Map[String, TcFunder[ETC.type]], vocab: CpVocab
	): Validated[Map[String, Seq[TcFunding[ETC.type]]]] = {
		val tcIdToFundingVs = lookups.map{implicit lookup =>
			for(
				stationTcId <- lookUp(Vars.stationTcId).require("missing ETC technical station id in funding table");
				funderName <- lookUp(Vars.fundingOrgName).require(s"missing funder name for funding of station $stationTcId");
				awardTitle <- lookUp(Vars.fundingAwardTitle).optional;
				awardNumber <- lookUp(Vars.fundingAwardNumber).require(s"Award number missing for funding of station $stationTcId");
				fStart <- getLocalDate(Vars.fundingStart).optional;
				fEnd <- getLocalDate(Vars.fundingEnd).optional;
				comment <- lookUp(Vars.fundingComment).optional;
				url <- lookUp(Vars.fundingAwardUri).map(uriStr => new URI(uriStr)).optional;
				tcFunder <- new Validated(funders.get(funderName)).require(s"Funder lookup failed for $funderName")
			) yield {
				val cpId = UriId.escaped(s"${stationTcId}_$awardNumber")
				val core = Funding(
					self = UriResource(
						uri = vocab.getFunding(cpId).toJava,
						label = None, //will be enriched later
						comments = comment.toSeq
					),
					awardNumber = Some(awardNumber),
					awardTitle = awardTitle,
					funder = tcFunder.core,
					awardUrl = url,
					start = fStart,
					stop = fEnd
				)
				stationTcId -> TcFunding[ETC.type](cpId, tcFunder, core)
			}
		}
		Validated.sequence(tcIdToFundingVs).map{tcToFundings =>
			tcToFundings.distinctBy(_._2.cpId).groupMap(_._1)(_._2)
		}
	}

	private def getSingleFileMeta(
		stationLookupV: Validated[Map[Int, StationId]]
	)(implicit lookup: Lookup): Validated[(EtcFileMetaKey, EtcFileMeta)] =
		for(
			stationTcId <- lookUp(Vars.stationTcId).map(_.toInt).require("technical station id missing");
			stationLookup <- stationLookupV;
			stationId <- new Validated(stationLookup.get(stationTcId))
				.require(s"Could not find ETC site id for technical station id $stationTcId");
			fileId <- lookUp(Vars.fileId).map(_.toInt).require("wrong or missing file id");
			loggerId <- lookUp(Vars.fileLoggerId).map(_.toInt).require("wrong or missing logger id");
			isBinary <- lookUp(Vars.fileFormat).collect{
					case "ASCII" => false
					case "Binary" => true
				}.require("file format must be 'ASCRII' or 'Binary'");
			fileType <- lookUp(Vars.fileType).map(DataType.withName)
		) yield {
			EtcFileMetaKey(station = stationId, loggerId = loggerId, fileId = fileId, dataType = fileType) ->
				EtcFileMeta(fileType, isBinary)
		}

	def toCET(ldt: LocalDateTime): Instant = ldt.atOffset(ZoneOffset.ofHours(1)).toInstant
	def toCETnoon(ld: LocalDate): Instant = toCET(LocalDateTime.of(ld, LocalTime.of(12, 0)))

	def parseTsv(bs: ByteString): Seq[Lookup] = {
		val lines = scala.io.Source.fromString(bs.utf8String).getLines()
		val colNames: Array[String] = lines.next().split('\t').map(_.trim)
		lines.map{lineStr =>
			colNames.zip(lineStr.split('\t').map(_.trim)).toMap
		}.toSeq
	}

	def getStation(
		fundingsV: Validated[Map[String, Seq[TcFunding[ETC.type]]]]
	)(implicit lookup: Lookup): Validated[EtcStation] = for(
		pos <- getPosition;
		tcIdStr <- lookUp(Vars.stationTcId);
		fundingsLookup <- fundingsV;
		name <- lookUp(Vars.siteName);
		id <- lookUp(Vars.siteId);
		stClass <- lookUp(Vars.stationClass).flatMap(AtcMetaSource.parseStationClass).optional;
		countryCode <- getCountryCode(id.take(2)).optional;
		climZone <- lookUp(Vars.climateZone).flatMap(parseClimateZone).optional;
		ecoType <- lookUp(Vars.ecosystemIGBP).flatMap(parseIgbpEcosystem).optional;
		meanTemp <- lookUp(Vars.annualTemp).map(_.toFloat).optional;
		meanPrecip <- lookUp(Vars.annualPrecip).map(_.toFloat).optional;
		meanRadiation <- lookUp(Vars.annualRad).map(_.toFloat).optional;
		descr <- lookUp(Vars.descr).optional;
		picture <- lookUp(Vars.pictureUrl).map(s => new URI(s.replace("download", "preview"))).optional;
		pubDois <- lookUp(Vars.stationDataPubDois).flatMap(parseDoiUris).optional;
		docDois <- lookUp(Vars.stationDocDois).flatMap(parseDoiUris).optional;
		tzOffset <- lookUp(Vars.timeZoneOffset).map(_.toInt).optional
	) yield {
		val fundings = fundingsLookup.get(tcIdStr).getOrElse(Nil).map{orig =>
			val label = orig.core.awardTitle.getOrElse("?") + " to " + name
			val coreFunding = orig.core.copy(self = orig.core.self.copy(label = Some(label)))
			orig.copy(core = coreFunding)
		}
		TcStation[E](
			cpId = TcConf.stationId[E](UriId.escaped(id)),
			tcId = makeId(tcIdStr),
			core = Station(
				org = Organization(
					self = UriResource(dummyUri, Some(id), descr.toSeq),
					name = name,
					email = None,
					website = None
				),
				id = id,
				coverage = pos,
				responsibleOrganization = None,
				pictures = picture.toSeq,
				specificInfo = EtcStationSpecifics(
					theme = None,
					stationClass = stClass,
					countryCode = countryCode,
					labelingDate = None, //not provided by TCs
					discontinued = false, //not provided by TCs
					climateZone = climZone,
					ecosystemType = ecoType,
					meanAnnualTemp = meanTemp,
					meanAnnualPrecip = meanPrecip,
					meanAnnualRad = meanRadiation,
					stationDocs = docDois.getOrElse(Nil),
					stationPubs = pubDois.getOrElse(Nil),
					timeZoneOffset = tzOffset,
					documentation = Nil//docs are not provided by TCs
				),
				funding = Option(fundings.map(_.core)).filterNot(_.isEmpty)
			),
			responsibleOrg = None,
			funding = fundings
		)
	}


	def getMembership(
		people: Map[TcId[E], EtcPerson],
		stations: Map[TcId[E], EtcStation]
	)(implicit lookup: Lookup): Validated[EtcMembership] = {
		val require = requireVar("membership") _
		for(
			persId <- require(Vars.persId);
			stationTcId <- require(Vars.stationTcId);
			roleStr <- require(Vars.role);
			roleOpt <- new Validated(rolesLookup.get(roleStr)).require(s"Unknown ETC role: $roleStr");
			role <- new Validated(roleOpt);
			roleEnd <- getLocalDate(Vars.roleEnd).map(toCETnoon).optional;
			roleStart <- getLocalDate(Vars.roleStart).map(toCETnoon).optional;
			person <- new Validated(people.get(makeId(persId))).require(s"Person not found for tcId = $persId");
			contribWeight <- lookUp(Vars.authorOrder).map(s => - s.toInt).optional;
			station <- new Validated(stations.get(makeId(stationTcId))).require(s"Station not found for tcId = $stationTcId (persId = $persId)")
		) yield {
			val assumedRole = new AssumedRole[E](role, person, station, contribWeight, None)
			Membership(UriId(""), assumedRole, roleStart, roleEnd)
		}
	}

	def getInstrument(implicit lookup: Lookup): Validated[EtcInstrument] ={
		val require = requireVar("instrument") _
		for(
			stId <- require(Vars.stationTcId).map(_.toInt);
			loggerId <- require(Vars.loggerId).map(_.toInt);
			sn <- require(Vars.loggerSerial);
			model <- require(Vars.loggerModel)
		) yield
			TcInstrument[E](
				tcId = CpVocab.getEtcInstrTcId(stId, loggerId),
				model = model,
				sn = sn
			)
	}

	private def requireVar(hint: String)(varName: String)(implicit lookup: Lookup) =
		lookUp(varName).require(s"$varName is required for $hint info")

	private val koppenZones = Set(
		"Af", "Am", "Aw/As", "BSh", "BWh", "BWk", "Cfa", "Cfb", "Cfc", "Csa", "Csb", "Csc", "Cwa", "Cwb", "Cwc",
		"Dfa", "Dfb", "Dfc", "Dfd", "Dsa", "Dsb", "Dsc", "Dsd", "Dwa", "Dwb", "Dwc", "Dwd", "EF", "ET"
	)

	def parseClimateZone(cz: String): Validated[UriResource] =
		if(koppenZones.contains(cz)) Validated.ok{
			val czUrl = cz.replace("/", "_") // Aw/As => Aw_As
			UriResource(new URI(s"${CpmetaVocab.MetaPrefix}koppen_$czUrl"), Some(cz), Nil)
		} else Validated.error(s"$cz is not a known Köppen-Geiger climate zone")


	private val igbpEcosystems = Set(
		"BSV", "CRO", "CSH", "CVM", "DBF", "DNF", "EBF", "ENF", "GRA",
		"MF", "OSH", "SAV", "SNO", "URB", "WAT", "WET", "WSA"
	)

	def parseIgbpEcosystem(eco: String): Validated[UriResource] = {
		if(igbpEcosystems.contains(eco)) Validated.ok(
			UriResource(new URI(s"${CpmetaVocab.MetaPrefix}igbp_$eco"), Some(eco), Nil)
		) else Validated.error(s"$eco is not a known IGBP ecosystem type")
	}

	def parseDoiUris(s: String): Validated[Seq[URI]] = {
		val valids = s.split("\\|").map(_.trim).filter(!_.isEmpty).map{ustr =>
			Validated(new URI(ustr)).require(s"Failed parsing $s as |-separated URI list")
		}.toIndexedSeq
		Validated.sequence(valids)
	}

	val dummyUri = new URI(CpmetaVocab.MetaPrefix + "dummy")
}
