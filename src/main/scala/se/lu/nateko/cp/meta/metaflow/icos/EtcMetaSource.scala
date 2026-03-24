package se.lu.nateko.cp.meta.metaflow.icos

import scala.language.unsafeNulls

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.scaladsl.Source
import akka.stream.{ActorAttributes, Materializer, Supervision}
import akka.util.ByteString
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.EtcConfig
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.{
	Orcid,
	Position,
	CountryCode,
	Organization,
	UriResource,
	Funder,
	Funding,
	Station,
	EtcStationSpecifics,
	Network,
	PositionUtil
}
import se.lu.nateko.cp.meta.core.etcupload.{DataType, StationId}
import se.lu.nateko.cp.meta.ingestion.badm.{Badm, BadmLocalDate, BadmLocalDateTime, BadmYear}
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.services.upload.etc.*
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.{Instant, LocalDate, LocalDateTime, LocalTime, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure


class EtcMetaSource(conf: EtcConfig, vocab: CpVocab, fetchTsv: EtcMetaSource.TsvFetcher)(using system: ActorSystem) extends TcMetaSource[ETC.type] {
	import EtcMetaSource.*
	import system.dispatcher

	private val log = Logging.getLogger(system, this)

	override def state: Source[State, () => Unit] = Source
		.tick(35.seconds, 5.hours, NotUsed)
		.mapAsync(1){_ =>
			fetchFromEtc().andThen{
				case Failure(err) =>
					log.error(err, "ETC metadata fetching/parsing error")
			}
		}
		.withAttributes(ActorAttributes.supervisionStrategy(_ => Supervision.Resume))
		.mapConcat{validated =>
			if(!validated.errors.isEmpty) log.warning("ETC metadata problem(s): " + validated.errors.distinct.mkString("\n"))
			if(validated.result.isEmpty) log.error("ETC metadata parsing has failed, preceding warnings may give a clue")
			validated.result.toList
		}
		.mapMaterializedValue(c => () => {c.cancel(); ()})


	private def fetchFromEtc(): Future[Validated[State]] =
		val peopleFut = fetchFromTsv(TableType.People, getPerson)

		val futfutValVal = for
			peopleVal <- peopleFut;
			stationsVal <- fetchStations();
			sensorsVal <- fetchSensors(stationsVal);
			instrumentsVal <- fetchFromTsv(TableType.Instruments, getLogger(sensorsVal))
		yield Validated.liftFuture:
			for(
				people <- peopleVal;
				stations <- stationsVal;
				sensors <- sensorsVal;
				instruments <- instrumentsVal
			) yield
				val membExtractor: Lookup ?=> Validated[EtcMembership] = getMembership(
					people.flatMap(p => p.tcIdOpt.map(_ -> p)).toMap,
					stations.map(s => s.tcId -> s).toMap
				)
				fetchFromTsv(TableType.Roles, membExtractor).map(_.map{membs =>
					//TODO Consider that after mapping to CP roles, a person may (in theory) have duplicate roles at the same station
					new TcState(stations, membs, instruments ++ sensors.filterNot(_.deployments.isEmpty))
				})

		futfutValVal.flatten.map(_.flatMap(identity))
	end fetchFromEtc

	private def fetchStations(): Future[Validated[Seq[EtcStation]]] = {
		for(
			fundLookupV <- fetchAndParseTsv(TableType.Funding).map{lookups =>
				for(
					fundersLookup <- parseFunders(vocab, lookups);
					fundings <- parseFundings(lookups, fundersLookup, vocab)
				) yield fundings
			};
			stations <- fetchFromTsv(TableType.Stations, getStation(fundLookupV))
		) yield stations
	}

	def getFileMeta: Future[Validated[EtcFileMetadataStore]] = {
		val utcFut = fetchFromTsv(TableType.Stations, getSiteUtc)

		val fileMetaFut = utcFut.flatMap{utcInfo =>
			val idLookup = utcInfo.map(_.map{
				case (tcId, id, _) => tcId -> id
			}.toMap)
			fetchFromTsv[(EtcFileMetaKey, EtcFileMeta)](TableType.Files, getSingleFileMeta(idLookup))
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

	private def fetchSensors(stationsVal: Validated[Seq[EtcStation]]): Future[Validated[Seq[EtcInstrument]]] =
		val futfutValVal = for
			modelDictVal <- getSensorModelDict;
			compDictVal <- getCompaniesDict;
			deplDictVal <- stationsVal.map(getDeploymentsDict).liftFuture
		yield Validated.liftFuture:
			for(modelDict <- modelDictVal; compDict <- compDictVal; deplDict <- deplDictVal.flatten) yield
				fetchFromTsv(TableType.Sensors, getSensor(modelDict, compDict, deplDict))
		futfutValVal.flatten.map(_.flatten)


	private def getCompaniesDict: Future[Validated[Map[Int, EtcCompany]]] =
		fetchFromTsv(TableType.Companies, getCompany(vocab)).map(_.map(_.toMap))

	private def getSensorModelDict: Future[Validated[Map[String, SensorModel]]] =
		fetchFromTsv(TableType.SensorModels, getSensorModel).map(_.map(_.toMap))

	private def getDeploymentsDict(stations: Seq[EtcStation]): Future[Validated[Map[String, Seq[InstrumentDeployment[E]]]]] = {
		val stationLookup = stations.map(s => s.tcId -> s).toMap

		fetchFromTsv(TableType.Meteosens, getSensorDeployment(stationLookup)).map(_.map(mergeInstrDeployments))
	}

	private def fetchAndParseTsv(tableType: TableType): Future[Seq[Lookup]] = fetchTsv(tableType)

	private def fetchFromTsv[T](tableType: TableType, extractor: Lookup ?=> Validated[T]): Future[Validated[Seq[T]]] =
		fetchAndParseTsv(tableType).map(lookups => Validated.sequence(lookups.map(extractor(using _))))

}

object EtcMetaSource{

	type Lookup = Map[String, String]
	type TsvFetcher = TableType => Future[Seq[Lookup]]

	def apply(conf: EtcConfig, vocab: CpVocab)(using system: ActorSystem, mat: Materializer): EtcMetaSource = {
		import system.dispatcher
		val baseEtcApiUrl = Uri(conf.metaService.toString)

		val fetcher: TsvFetcher = tableType => {
			Http()
			.singleRequest(HttpRequest(
				uri = baseEtcApiUrl.withQuery(Uri.Query("type" -> tableType.urlParam))
			))
			.flatMap(_.entity.toStrict(3.seconds))
			.map(ent => parseTsv(ent.data))
		}

		new EtcMetaSource(conf, vocab, fetcher)
	}


	type E = ETC.type
	private type EtcInstrument = TcInstrument[E]
	private type EtcPerson = TcPerson[E]
	private type EtcStation = TcStation[E]
	private type EtcCompany = TcGenericOrg[E]
	private type EtcMembership = Membership[E]
	private class SensorModel(val modelId: String, val compId: Int, val name: String, val description: Option[String])
	given Envri = Envri.ICOS


	def makeId(id: String): TcId[E] = EtcConf.makeId(id)

	enum TableType(val urlParam: String) {
		case Roles extends TableType("teamrole")
		case People extends TableType("team")
		case Stations extends TableType("station")
		case Companies extends TableType("companies")
		case Instruments extends TableType("logger")
		case SensorModels extends TableType("models2")
		case Sensors extends TableType("sensors")
		case Meteosens extends TableType("meteosens2")
		case Files extends TableType("file")
		case Funding extends TableType("funding")
	}

	private enum Var(val colName: String):
		case StationLat extends Var("LOCATION_LAT")
		case StationLon extends Var("LOCATION_LONG")
		case StationElev extends Var("LOCATION_ELEV")
		case Fname extends Var("TEAM_MEMBER_FIRSTNAME")
		case Lname extends Var("TEAM_MEMBER_LASTNAME")
		case Email extends Var("TEAM_MEMBER_EMAIL")
		case OrcidId extends Var("TEAM_MEMBER_ORCID")
		case MemberRole extends Var("TEAM_MEMBER_ROLE")
		case RoleStart extends Var("TEAM_MEMBER_WORKSTART")
		case RoleEnd extends Var("TEAM_MEMBER_WORKEND")
		case AuthorOrder extends Var("TEAM_MEMBER_AUTHORDER")
		case PersId extends Var("ID_TEAM")
		case CompanyTcId extends Var("ID_COMPANY")
		case CompanyName extends Var("COMPANY")
		case StationTcId extends Var("ID_STATION")
		case SiteName extends Var("SITE_NAME")
		case SiteId extends Var("SITE_ID")
		case StationClass extends Var("CLASS_ICOS")
		case Descr extends Var("SITE_DESC")
		case PictureUrl extends Var("URL_PICTURE")
		case UtcOffset extends Var("UTC_OFFSET")
		case AnnualTemp extends Var("MAT")
		case AnnualPrecip extends Var("MAP")
		case AnnualRad extends Var("MAR")
		case ClimateZone extends Var("CLIMATE_KOPPEN")
		case EcosystemIGBP extends Var("IGBP")
		case StationDocDois extends Var("REFERENCE_DOI_D")
		case StationDataPubDois extends Var("REFERENCE_DOI_P")
		case TimeZoneOffset extends Var("UTC_OFFSET")
		case LoggerSensorId extends Var("LOGGER_SENSOR_ID")
		case LoggerId extends Var("LOGGER_ID")
		case SensorModelId extends Var("ID_MODEL")
		case SensorName extends Var("NAME")
		case SensorDescription extends Var("DESCRIPTION")
		case SensorId extends Var("ID_SENSOR")
		case SensorSerial extends Var("SN")
		case SensorVar extends Var("VARIABLE")
		case SensorLat extends Var("LAT")
		case SensorNorthSouthOffset extends Var("NSDIST")
		case SensorLon extends Var("LONG")
		case SensorEastWestOffset extends Var("EWDIST")
		case SensorHeight extends Var("HEIGHT")
		case DeploymentStart extends Var("START_DATE")
		case FileId extends Var("FILE_ID")
		case FileLoggerId extends Var("FILE_LOGGER_ID")
		case FileFormat extends Var("FILE_FORMAT")
		case FileType extends Var("FILE_TYPE")
		case FundingOrgName extends Var("FUNDING_ORGANIZATION")
		case FundingAwardNumber extends Var("FUNDING_GRANT")
		case FundingAwardUri extends Var("FUNDING_GRANT_URL")
		case FundingAwardTitle extends Var("FUNDING_TITLE")
		case FundingStart extends Var("FUNDING_DATE_START")
		case FundingEnd extends Var("FUNDING_DATE_END")
		case FundingComment extends Var("FUNDING_COMMENT")
		case NetworkName extends Var("NETWORK")

	private val rolesLookup: Map[String, Option[Role]] = Map(
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

	def lookUp(colName: String)(using lookup: Lookup): Validated[String] =
		new Validated(lookup.get(colName).filter(_.length > 0))

	private def lookUp(variable: Var)(using Lookup): Validated[String] = lookUp(variable.colName)

	def lookUpOrcid(colName: String)(using Lookup): Validated[Option[Orcid]] =
		lookUp(colName).optional.flatMap{
			case Some(Orcid(orc)) => Validated.ok(Some(orc))
			case None => Validated.ok(None)
			case Some(badOrcid) => new Validated(None, Seq(s"Could not parse Orcid id from string $badOrcid"))
		}

	private def lookUpOrcid(v: Var)(using Lookup): Validated[Option[Orcid]] =
		lookUpOrcid(v.colName)

	private def getNumber(variable: Var)(using Lookup): Validated[Number] = lookUp(variable).flatMap{
		str => Validated(Badm.numParser.parse(str)).require(s"${variable.colName} must have been a number (was $str)")
	}

	private def getLocalDateTime(
		variable: Var, defaultTime: LocalTime, defaultMonth: Int, defaultDay: Int
	)(using Lookup): Validated[LocalDateTime] = lookUp(variable).flatMap{
		case Badm.Date(BadmLocalDateTime(dt)) => Validated.ok(dt)
		case Badm.Date(BadmLocalDate(date)) => Validated.ok(date.atTime(defaultTime))
		case Badm.Date(BadmYear(year)) => Validated.ok(LocalDate.of(year, defaultMonth, defaultDay).atTime(defaultTime))
		case bv => Validated.error(s"${variable.colName} must have been a BADM-format local date(-time) (was $bv)")
	}

	private def getLocalDate(variable: Var, defaultMonth: Int, defaultDay: Int)(using Lookup): Validated[LocalDate] =
		getLocalDateTime(variable, LocalTime.NOON, defaultMonth, defaultDay).map(_.toLocalDate)

	private def getStationPosition(using Lookup): Validated[Option[Position]] =
		for(
			latOpt <- getNumber(Var.StationLat).optional;
			lonOpt <- getNumber(Var.StationLon).optional;
			alt <- getNumber(Var.StationElev).optional
		) yield for(lat <- latOpt; lon <- lonOpt)
			yield Position(lat.doubleValue, lon.doubleValue, alt.map(_.floatValue), None, None)

	private def getPerson(using Lookup): Validated[EtcPerson] =
		for(
			fname <- lookUp(Var.Fname).require("person must have first name");
			lname <- lookUp(Var.Lname).require("person must have last name");
			tcId <- lookUp(Var.PersId).require("unique ETC's id is required for a person");
			email <- lookUp(Var.Email).optional;
			orcid <- lookUpOrcid(Var.OrcidId);
			cpId = CpVocab.getPersonCpId(fname, lname)
		) yield
			TcPerson(cpId, Some(makeId(tcId)), fname, lname, email.map(_.toLowerCase), orcid)

	private def getCountryCode(stId: StationId): Validated[CountryCode] = getCountryCode(stId.id.take(2))

	private def getCountryCode(s: String): Validated[CountryCode] = s match{
		case CountryCode(cc) => Validated.ok(cc)
		case _ => Validated.error(s + " is not a valid country code")
	}

	private def getCompany(vocab: CpVocab)(using Lookup): Validated[(Int, EtcCompany)] =
		for(
			tcId <- getNumber(Var.CompanyTcId).map(_.intValue).require("company must have integer id");
			name <- lookUp(Var.CompanyName).require("company must have a name");
			cpId = UriId(s"etcorg_$tcId");
			orgUri = vocab.getOrganization(cpId).toJava;
			core = Organization(UriResource(orgUri, None, Nil), name, None, None, None)
		) yield tcId -> TcGenericOrg(cpId, Some(makeId(tcId.toString)), core)

	private def getSensorModel(using Lookup): Validated[(String, SensorModel)] =
		for(
			modelId <- lookUp(Var.SensorModelId).require("sensor model must have id");
			compId <- getNumber(Var.CompanyTcId).map(_.intValue).require("sensor model must have vendor company id");
			name <- lookUp(Var.SensorName).require("sensor model must have name");
			descript <- lookUp(Var.SensorDescription).optional
		) yield modelId -> new SensorModel(modelId, compId, name, descript)

	private def getSiteUtc(using Lookup): Validated[(Int, StationId, Option[Int])] =
		for(
			techId <- lookUp(Var.StationTcId).map(_.toInt);
			stationId <- lookUp(Var.SiteId).map{case StationId(sId) => sId};
			utc <- lookUp(Var.UtcOffset).map(_.toInt).optional
		) yield (techId, stationId, utc)

	private def parseFunders(vocab: CpVocab, lookups: Seq[Lookup]): Validated[Map[String, TcFunder[ETC.type]]] = Validated
		.sequence(lookups.map(lookUp(Var.FundingOrgName)(using _)))
		.map(
			_.distinct.map{funderName =>
				val cpId = UriId.escaped(funderName)
				val org = Organization(
					self = UriResource(vocab.getOrganization(cpId).toJava, None, Nil),
					name = funderName,
					email = None,
					website = None,
					webpageDetails = None
				)
				funderName -> TcFunder[ETC.type](
					cpId = cpId,
					tcIdOpt = Some(makeId(s"fund_${funderName.hashCode}")),
					core = Funder(org, None)
				)
			}.toMap
		)

	private def parseFundings(
		lookups: Seq[Lookup], funders: Map[String, TcFunder[ETC.type]], vocab: CpVocab
	): Validated[Map[String, Seq[TcFunding[ETC.type]]]] = {
		val tcIdToFundingVs = lookups.map{lookup =>
			given Lookup = lookup
			for
				stationTcId <- lookUp(Var.StationTcId).require("missing ETC technical station id in funding table");
				funderName <- lookUp(Var.FundingOrgName).require(s"missing funder name for funding of station $stationTcId");
				awardTitle <- lookUp(Var.FundingAwardTitle).optional;
				awardNumber <- lookUp(Var.FundingAwardNumber).optional;
				fStart <- getLocalDate(Var.FundingStart, 1, 1).optional;
				fEnd <- getLocalDate(Var.FundingEnd, 12, 1).optional;
				comment <- lookUp(Var.FundingComment).optional;
				url <- lookUp(Var.FundingAwardUri).map(uriStr => new URI(uriStr)).filter(_.isAbsolute).optional;
				tcFunder <- new Validated(funders.get(funderName)).require(s"Funder lookup failed for $funderName")
			yield
				val idComps: Seq[String] = stationTcId +:
					awardNumber.map(Seq(_)).getOrElse:
						val funderId: String = tcFunder.tcIdOpt.fold(tcFunder.cpId.toString)(_.id)
						funderId +: Seq(fStart, fEnd).flatten.map(_.toString)
				val cpId = UriId.escaped(idComps.mkString("_"))
				val core = Funding(
					self = UriResource(
						uri = vocab.getFunding(cpId).toJava,
						label = None, //will be enriched later
						comments = comment.toSeq
					),
					awardNumber = awardNumber,
					awardTitle = awardTitle,
					funder = tcFunder.core,
					awardUrl = url,
					start = fStart,
					stop = fEnd
				)
				stationTcId -> TcFunding[ETC.type](cpId, tcFunder, core)
		}
		Validated.sequence(tcIdToFundingVs).map{tcToFundings =>
			tcToFundings.distinctBy(_._2.cpId).groupMap(_._1)(_._2)
		}
	}

	private def getSingleFileMeta(
		stationLookupV: Validated[Map[Int, StationId]]
	)(using Lookup): Validated[(EtcFileMetaKey, EtcFileMeta)] =
		for(
			stationTcId <- lookUp(Var.StationTcId).map(_.toInt).require("technical station id missing");
			stationLookup <- stationLookupV;
			stationId <- new Validated(stationLookup.get(stationTcId))
				.require(s"Could not find ETC site id for technical station id $stationTcId");
			fileId <- lookUp(Var.FileId).map(_.toInt).require("wrong or missing file id");
			loggerId <- lookUp(Var.FileLoggerId).map(_.toInt).require("wrong or missing logger id");
			isBinary <- lookUp(Var.FileFormat).collect{
					case "ASCII" => false
					case "Binary" => true
				}.require("file format must be 'ASCII' or 'Binary'");
			fileType <- lookUp(Var.FileType).map(DataType.valueOf)
		) yield {
			EtcFileMetaKey(station = stationId, loggerId = loggerId, fileId = fileId, dataType = fileType) ->
				EtcFileMeta(fileType, isBinary)
		}

	def toCET(ldt: LocalDateTime): Instant = ldt.atOffset(ZoneOffset.ofHours(1)).toInstant
	def toCETnoon(ld: LocalDate): Instant = toCET(LocalDateTime.of(ld, LocalTime.of(12, 0)))

	private def parseTsv(bs: ByteString): Seq[Lookup] = {

		def parseCells(line: String): Array[String] = line
			.split("\\t", -1).map(_.trim.stripPrefix("\"").stripSuffix("\"").replaceAll("\"\"", "\""))

		val lines = scala.io.Source.fromString(bs.utf8String).getLines()
		val colNames: Array[String] = parseCells(lines.next())

		lines.map(lineStr => colNames.zip(parseCells(lineStr)).toMap).toSeq
	}

	// Public for testing
	def getStation(
		fundingsV: Validated[Map[String, Seq[TcFunding[ETC.type]]]]
	)(using Lookup): Validated[EtcStation] = for(
		pos <- getStationPosition;
		tcIdStr <- lookUp(Var.StationTcId);
		fundingsLookup <- fundingsV;
		name <- lookUp(Var.SiteName);
		id <- lookUp(Var.SiteId);
		etcStationId <- new Validated(StationId.unapply(id)).require(s"$id is not a proper ETC station id");
		stClass <- lookUp(Var.StationClass).flatMap(AtcMetaSource.parseStationClass).optional;
		countryCode <- getCountryCode(id.take(2)).optional;
		climZone <- lookUp(Var.ClimateZone).flatMap(parseClimateZone).optional;
		ecoType <- lookUp(Var.EcosystemIGBP).flatMap(parseIgbpEcosystem).optional;
		meanTemp <- lookUp(Var.AnnualTemp).map(_.toFloat).optional;
		meanPrecip <- lookUp(Var.AnnualPrecip).map(_.toFloat).optional;
		meanRadiation <- lookUp(Var.AnnualRad).map(_.toFloat).optional;
		descr <- lookUp(Var.Descr).optional;
		picture <- lookUp(Var.PictureUrl).map(s => new URI(s.replace("download", "preview"))).optional;
		pubDois <- lookUp(Var.StationDataPubDois).flatMap(parseDoiUris).optional;
		docDois <- lookUp(Var.StationDocDois).flatMap(parseDoiUris).optional;
		tzOffset <- lookUp(Var.TimeZoneOffset).map(_.toInt).optional;
		networkNames <- lookUp(Var.NetworkName).optional
	) yield {
		val fundings = fundingsLookup.get(tcIdStr).getOrElse(Nil).map{orig =>
			val label = orig.core.awardTitle.getOrElse("?") + " to " + name
			val coreFunding = orig.core.copy(self = orig.core.self.copy(label = Some(label)))
			orig.copy(core = coreFunding)
		}

		val networks = networkNames.map(parseBarSeparated).getOrElse(Nil).map(name =>
			TcNetwork[E](cpId = UriId(name), core = Network(dummyUri))
		)

		TcStation[E](
			cpId = CpVocab.etcStationUriId(etcStationId),
			tcId = makeId(tcIdStr),
			core = Station(
				org = Organization(
					self = UriResource(dummyUri, Some(id), descr.toSeq),
					name = name,
					email = None,
					website = None,
					webpageDetails = None
				),
				id = id,
				location = pos,
				coverage = None,
				responsibleOrganization = None,
				pictures = picture.toSeq,
				countryCode = countryCode,
				specificInfo = EtcStationSpecifics(
					theme = None,
					stationClass = stClass,
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
				funding = Option(fundings.map(_.core)).filterNot(_.isEmpty),
				networks = networks.map(_.core)
			),
			responsibleOrg = None,
			funding = fundings,
			networks = networks
		)
	}


	private def getMembership(
		people: Map[TcId[E], EtcPerson],
		stations: Map[TcId[E], EtcStation]
	)(using Lookup): Validated[EtcMembership] = {
		val require = requireVar("membership") _
		for(
			persId <- require(Var.PersId);
			stationTcId <- require(Var.StationTcId);
			roleStr <- require(Var.MemberRole);
			roleOpt <- new Validated(rolesLookup.get(roleStr)).require(s"Unknown ETC role: $roleStr");
			role <- new Validated(roleOpt);
			roleEnd <- getLocalDate(Var.RoleEnd, 12, 1).map(toCETnoon).optional;
			roleStart <- getLocalDate(Var.RoleStart, 1, 1).map(toCETnoon).optional;
			person <- new Validated(people.get(makeId(persId))).require(s"Person not found for tcId = $persId");
			contribWeight <- lookUp(Var.AuthorOrder).map(s => - s.toInt).optional;
			station <- new Validated(stations.get(makeId(stationTcId))).require(s"Station not found for tcId = $stationTcId (persId = $persId)")
		) yield {
			val assumedRole = new AssumedRole[E](role, person, station, contribWeight, None)
			Membership(UriId(""), assumedRole, roleStart, roleEnd)
		}
	}

	private def getLogger(sensorsVal: Validated[Seq[EtcInstrument]])(using Lookup): Validated[EtcInstrument] = {
		val sensorsDictVal = sensorsVal.map(_.map(sens => sens.tcId -> sens).toMap)
		val require = requireVar("instrument") _
		for(
			stId <- require(Var.StationTcId).map(_.toInt);
			loggerId <- require(Var.LoggerId).map(_.toInt);
			sensorTcId <- require(Var.LoggerSensorId);
			sensorDict <- sensorsDictVal;
			instr <- new Validated(sensorDict.get(makeId(sensorTcId))).require(s"Could not look up logger by sensor id $sensorTcId")
		) yield
			instr.copy(tcId = CpVocab.getEtcInstrTcId(stId, loggerId))
	}

	private def getSensor(
		modelDict: Map[String, SensorModel],
		compDict: Map[Int, EtcCompany],
		deploymentsDict: Map[String, Seq[InstrumentDeployment[E]]]
	)(using Lookup): Validated[EtcInstrument] = for(
		tcIdStr <- lookUp(Var.SensorId).require("sensor must have id");
		modelId <- lookUp(Var.SensorModelId).require("sensor must have model id");
		serial <- lookUp(Var.SensorSerial).require("sensor must have serial number");
		model <- new Validated(modelDict.get(modelId)).require(s"Sensor model (id = $modelId) not found");
		vendor <- new Validated(compDict.get(model.compId)).require(s"Sensor vendor with id = ${model.compId} was not found").optional
	) yield
		TcInstrument[E](
			tcId = makeId(tcIdStr),
			model = model.name,
			comment = model.description,
			sn = serial,
			vendor = vendor,
			deployments = deploymentsDict.getOrElse(tcIdStr, Nil)
		)

	private def getSensorDeployment(
		stationLookup: Map[TcId[E], EtcStation]
	)(using Lookup): Validated[(String, InstrumentDeployment[E])] = for
		stationTcIdStr <- lookUp(Var.StationTcId).require("sensor deployment must have technical station id");
		varName <- lookUp(Var.SensorVar).optional;
		sensorId <- lookUp(Var.SensorId).require("sensor deployment must have sensor id");
		latOpt <- getNumber(Var.SensorLat).map(_.doubleValue).optional;
		northOpt <- getNumber(Var.SensorNorthSouthOffset).map(_.doubleValue).optional;
		lonOpt <- getNumber(Var.SensorLon).map(_.doubleValue).optional;
		eastOpt <- getNumber(Var.SensorEastWestOffset).map(_.doubleValue).optional;
		heightOpt <- getNumber(Var.SensorHeight).map(_.floatValue).optional;
		startLocal <- getLocalDateTime(Var.DeploymentStart, LocalTime.MIN, 1, 1).optional;
		stationTcId = makeId(stationTcIdStr);
		station <- new Validated(stationLookup.get(stationTcId)).require(s"Failed to look up a station with ETC id $stationTcId");
		tzOpt = station.core.specificInfo match{
			case etc: EtcStationSpecifics => etc.timeZoneOffset
			case _ => None
		};
		tz <- new Validated(tzOpt).require(s"Could not look up time zone offset for station with id $stationTcId");
		statPos <- new Validated(station.core.location).require(s"Position for station with id $stationTcId could not be looked up")
	yield
		inline val Rearth = 6371000d

		inline def latNaive = northOpt.map: north =>
			statPos.lat + Math.toDegrees(north / Rearth)

		inline def lonNaive = eastOpt.map: east =>
			val Rlat = Rearth * Math.cos(Math.toRadians(statPos.lat))
			statPos.lon + Math.toDegrees(east / Rlat)

		val pos = for
			lat <- latOpt.orElse(latNaive)
			lon <- lonOpt.orElse(lonNaive)
		yield Position(lat, lon, heightOpt, None, None)

		val start = startLocal.map(_.toInstant(ZoneOffset.ofHours(tz)))
		sensorId -> InstrumentDeployment(UriId(""), stationTcId, station.cpId, pos, varName, start, None)


	// Public for testing
	def mergeInstrDeployments(
		depls: Seq[(String, InstrumentDeployment[E])]
	): Map[String, Seq[InstrumentDeployment[E]]] = if(depls.isEmpty) Map.empty else {

		import se.lu.nateko.cp.meta.utils.slidingByKey

		val pass1 = depls.groupBy{
			case (_, depl) => (depl.stationTcId, depl.variable)
		}.values.flatMap{instDepls =>
			val sorted = instDepls.sortBy(_._2.start)

			val fused = slidingByKey(sorted.iterator){
				case (sensId, _) => sensId
			}.map{
				case IndexedSeq(single) => single
				case group @ IndexedSeq((sensId, depl), _*) =>
					sensId -> depl.copy(
						pos = PositionUtil.average(group.flatMap(_._2.pos))
					)
			}.toIndexedSeq

			fused.sliding(2,1).collect{
				case Seq((sensId, d1), (_, d2)) => sensId -> d1.copy(stop = d2.start)
			}.toSeq :+ fused.last
		}

		val deplOrd = Ordering.by((d: InstrumentDeployment[E]) => d.start).orElseBy(_.variable)

		pass1.groupMap{
			// group by sensor id and variable type (e.g. "TS" for TS_2_2_1)
			(sensorId, depl) => sensorId -> depl.variable.flatMap(CpVocab.getEcoVariableFamily).orElse(depl.variable)
		}(_._2)
		.toSeq
		.flatMap:
			case ((sensorId, _), depls) =>
				val deplsSorted = depls.toSeq.sortBy(_.start)
				val deplsWithStopDates = deplsSorted.sliding(2,1).collect{
					case Seq(d1, d2) =>
						d1.copy(stop = minOptInst(d1.stop, d2.start))
				}.toSeq :+ deplsSorted.last
				deplsWithStopDates.map(sensorId -> _)
		.groupMap(_._1)(_._2)
		.map:
			case (sensorId, depls) =>
				val deplsWithIds = depls.toSeq.sorted(using deplOrd).zipWithIndex.map:
					case (depl, idx) => depl.copy(cpId = new UriId(s"${sensorId}_$idx"))

				sensorId -> deplsWithIds
	}

	private def minOptInst(i1: Option[Instant], i2: Option[Instant]): Option[Instant] =
		Seq(i1, i2).flatten.sorted.headOption

	private def requireVar(hint: String)(variable: Var)(using Lookup) =
		lookUp(variable).require(s"${variable.colName} is required for $hint info")

	private val koppenZones = Set(
		"Af", "Am", "Aw/As", "BSh", "BWh", "BWk", "Cfa", "Cfb", "Cfc", "Csa", "Csb", "Csc", "Cwa", "Cwb", "Cwc",
		"Dfa", "Dfb", "Dfc", "Dfd", "Dsa", "Dsb", "Dsc", "Dsd", "Dwa", "Dwb", "Dwc", "Dwd", "EF", "ET"
	)

	private def parseClimateZone(cz: String): Validated[UriResource] =
		if(koppenZones.contains(cz)) Validated.ok{
			val czUrl = cz.replace("/", "_") // Aw/As => Aw_As
			UriResource(new URI(s"${CpmetaVocab.MetaPrefix}koppen_$czUrl"), Some(cz), Nil)
		} else Validated.error(s"$cz is not a known Köppen-Geiger climate zone")


	private val igbpEcosystems = Set(
		"BSV", "CRO", "CSH", "CVM", "DBF", "DNF", "EBF", "ENF", "GRA",
		"MF", "OSH", "SAV", "SNO", "URB", "WAT", "WET", "WSA"
	)

	private def parseIgbpEcosystem(eco: String): Validated[UriResource] = {
		if(igbpEcosystems.contains(eco)) Validated.ok(
			UriResource(new URI(s"${CpmetaVocab.MetaPrefix}igbp_$eco"), Some(eco), Nil)
		) else Validated.error(s"$eco is not a known IGBP ecosystem type")
	}

	private def parseDoiUris(input: String): Validated[Seq[URI]] = {
		Validated.sequence(
			parseBarSeparated(input).map(item =>
				Validated(new URI(item)).require(s"Failed parsing $input as URI")
			)
		)
	}

	private def parseBarSeparated(input: String): Seq[String] = {
		input.split("\\|").map(_.trim).filter(!_.isEmpty).toSeq
	}

	val dummyUri = new URI(CpmetaVocab.MetaPrefix + "dummy")
}
