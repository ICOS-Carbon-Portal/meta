package se.lu.nateko.cp.meta.metaflow.icos

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.MetaUploadConf
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.Validated

import java.io.{BufferedInputStream, FileInputStream, InputStreamReader}
import java.net.URI
import java.nio.file.Path
import java.time.{Instant, LocalDate}
import scala.util.Using

import EtcMetaSource.{Lookup, lookUp, lookUpOrcid, dummyUri}

class AtcMetaSource(conf: MetaUploadConf)(using ActorSystem) extends FileDropMetaSource[ATC.type](conf):
	import AtcMetaSource.*
	override def readState: Validated[State] = for(
			orgs <- readAllOrgs(getTableFile(instrumentsTbl), getTableFile(stationsTbl));
			stations <- parseStations(getTableFile(stationsTbl), orgs);
			instruments <- parseInstruments(getTableFile(instrumentsTbl), orgs);
			membs <- parseMemberships(getTableFile("combineContacts"), getTableFile("combineRoles"), stations)
		) yield
			new TcState(stations, membs, instruments)


object AtcMetaSource{
	type A = ATC.type
	import AtcConf.makeId
	private type OrgsMap = Map[TcId[A], TcPlainOrg[A]]

	val stationsTbl = "combineStations"
	val instrumentsTbl = "combineInstruments"
	val undefinedOrgId = "41"

	val IdCol = "#Id"
	val WigosIdCol = "WIGOSId"
	val StationIdCol = "ShortName"
	val StationNameCol = "FullName"
	val StationClassCol = "Class"
	val CountryCol = "Country"
	val LatCol = "Latitude"
	val LonCol = "Longitude"
	val AltCol = "EAS"
	val StationInstNameCol = "Institution"
	val StationInstIdCol = "InstitutionId"
	val StationInstWeb = "InstitutionWebSite"
	val TimeZoneCol = "TimeZone"

	val PersonIdCol = "#ContactId"
	val FirstNameCol = "ContactForename"
	val LastNameCol = "ContactLastName"
	val EmailCol = "Email"
	val OrcidCol = "Orcid"
	val AffiliationIdCol = "AffiliationId"
	val AffilOrgNameCol = "Affiliation"
	val AffilCountryCol = "Country"

	val RoleStationIdCol = "#StationId"
	val RolePersIdCol = "ContactId"
	val RoleIdCol = "RoleId"
	val RoleStartCol = "StartDate"
	val RoleEndCol = "EndDate"
	val SpecielListCol = "SpeciesList"

	val InstrIdCol = "#InstrumentId"
	val InstrNameCol = "InstrumentName"
	val InstrSerialCol = "SerialId"
	val InstrModelCol = "Model"
	val InstrOwnerIdCol = "OwnerId"
	val InstrOwnerCol = "Owner"
	val InstrVendorCol = "Manufacturer"
	val InstrVendorIdCol = "ManufacturerId"
	val InstrRelatedCol = "RelatedInstruments"

	val InfinitePast = "1900-01-01 00:00:00"

	private val countryMap = Map(
		"belgium"         -> "BE",
		"czech republic"  -> "CZ",
		"denmark"         -> "DK",
		"estonia"         -> "EE",
		"finland"         -> "FI",
		"france"          -> "FR",
		"germany"         -> "DE",
		"great britain"   -> "GB",
		"greece"          -> "GR",
		"greenland"       -> "GL",
		"hungary"         -> "HU",
		"united kingdom"  -> "GB",
		"ireland"         -> "IE",
		"italy"           -> "IT",
		"norway"          -> "NO",
		"poland"          -> "PL",
		"russia"          -> "RU",
		"spain"           -> "ES",
		"sweden"          -> "SE",
		"switzerland"     -> "CH",
		"the netherlands" -> "NL",
	)

	private val roleMap: Map[Int, Option[Role]] = Map(
		1  -> Some(Engineer), //Instrument responsible
		2  -> Some(PI), //Station PI
		10 -> Some(PI), //Station deputy PI
		5  -> Some(Administrator), //Station supervising PI
		4  -> Some(Engineer), //Tank configurator
		12 -> Some(PI), //Species PI
		9  -> None //Other station contact (disregarded by ICOS)
	)

	private val roleWeightMap: Map[Int, Int] = Map(
		5  -> 100,//supervising PI
		2  -> 70, //PI
		12 -> 40, //Species PI
		10 -> 20  //deputy PI
	)

	private def makeOrgId(base: String) = makeId("org_" + base)

	def parseCountryCode(s: String): Validated[CountryCode] = {
		val ccOpt = CountryCode.unapply(countryMap.getOrElse(s.trim.toLowerCase, s.trim))
		val errors = if(ccOpt.isEmpty) Seq(s"Neither a recognized country (in AtcMetaSource) nor a country code: $s") else Nil
		new Validated(ccOpt, errors)
	}

	def parseLocalDate(ts: String): Validated[LocalDate] = Validated(LocalDate.parse(ts.take(10)))

	def parseStationClass(s: String): Validated[IcosStationClass] =
		if s == "0" || s == "NO" then new Validated(None, Nil)
		else Validated(IcosStationClass.valueOf(s))

	def parseStations(path: Path, orgs: OrgsMap): Validated[IndexedSeq[TcStation[A]]] = parseFromCsv(path){
		val demand = lookUpMandatory(stationsTbl) _

		for(
			stIdStr <- demand(StationIdCol);
			tcId <- demand(IdCol);
			wigosId <- lookUp(WigosIdCol).optional;
			lat <- demand(LatCol).map(_.toDouble);
			lon <- demand(LonCol).map(_.toDouble);
			alt <- demand(AltCol).map(_.toFloat);
			stClass <- demand(StationClassCol).flatMap(parseStationClass).optional;
			name <- demand(StationNameCol);
			country <- demand(CountryCol).flatMap(parseCountryCode).optional;
			orgIdOpt <- lookUp(StationInstIdCol).map(makeOrgId).optional;
			tzOffset <- lookUp(TimeZoneCol).map(_.toInt).optional
		) yield TcStation[A](
			cpId = TcConf.stationId[A](UriId.escaped(stIdStr)),
			tcId = makeId(tcId),
			core = Station(
				org = Organization(
					self = UriResource(uri = EtcMetaSource.dummyUri, label = Some(stIdStr), comments = Nil),
					name = name,
					email = None,
					website = None,
					webpageDetails = None
				),
				id = stIdStr,
				location = Some(Position(lat, lon, Some(alt), Some(s"$name position"), None)),
				coverage = None,
				responsibleOrganization = None,
				pictures = Nil,
				countryCode = country,
				specificInfo = AtcStationSpecifics(
					wigosId = wigosId,
					theme = None,
					stationClass = stClass,
					labelingDate = None, //not provided by TCs
					discontinued = false, //not provided by TCs
					timeZoneOffset = tzOffset,
					documentation = Seq.empty//docs are not provided by the TCs
				),
				funding = None
			),
			responsibleOrg = orgIdOpt.flatMap(orgs.get),
			funding = Nil
		)
	}

	def parseMemberships(
		contacts: Path, roles: Path,
		stations: Seq[TcStation[A]]
	): Validated[IndexedSeq[Membership[A]]] = {

		val stationLookup = stations.map(s => s.tcId -> s).toMap

		val peopleLookupVal = parseFromCsv(contacts)(parsePerson).map{ppl =>
			(for(pers <- ppl; id <- pers.tcIdOpt) yield id -> pers).toMap
		}

		parseFromCsv(roles){
			val demand = lookUpMandatory("roles") _

			for(
				peopleLookup <- peopleLookupVal;
				stationIdStr <- demand(RoleStationIdCol);
				station <- new Validated(stationLookup.get(makeId(stationIdStr))).require(s"Could not find station with internal ATC id $stationIdStr");
				persIdStr <- demand(RolePersIdCol);
				person <- new Validated(peopleLookup.get(makeId(persIdStr))).require(s"Could not find person with internal ATC id $persIdStr");
				roleId <- lookUp(RoleIdCol).map(_.toInt).require("Problem parsing role id");
				roleOpt <- new Validated(roleMap.get(roleId)).require(s"Unknown ATC role with id $roleId");
				role <- new Validated(roleOpt);
				startDate <- lookUpDate(RoleStartCol);
				endDate <- lookUpDate(RoleEndCol);
				extra <- lookUp(SpecielListCol).filter(_ => roleId == 12).optional //only retain for Species PIs
			) yield {
				val assumedRole = new AssumedRole[A](role, person, station, roleWeightMap.get(roleId), extra)
				Membership(UriId(""), assumedRole, startDate, endDate)
			}
		}
	}

	def lookUpMandatory(tableName: String)(varName: String)(using row: Lookup): Validated[String] =
		lookUp(varName).require(s"$varName not found in $tableName table on row ${row.mkString(", ")}")

	def parseFromCsv[T](path: Path)(extractor: Lookup ?=> Validated[T]): Validated[IndexedSeq[T]] =
		parseFromSimpleCsv(path, ';')(extractor)

	def parseFromSimpleCsv[T](path: Path, sep: Char)(extractor: Lookup ?=> Validated[T]): Validated[IndexedSeq[T]] =
		import com.opencsv.{CSVParserBuilder, CSVReaderBuilder}
		import scala.jdk.CollectionConverters.IteratorHasAsScala

		val tryRes = Using.Manager: use =>
			val is = use(BufferedInputStream(FileInputStream(path.toFile)))

			// Skip possible byte-order mark in the file
			if is.markSupported() then
				is.mark(3)
				if Seq(0xEF, 0xBB, 0xBF).exists(_ != is.read())
				then is.reset()

			val reader = use(InputStreamReader(is))
			val parser = CSVParserBuilder().withSeparator(sep).build()
			val csvReader = CSVReaderBuilder(reader).withCSVParser(parser).build()
			val lines = csvReader.iterator().asScala

			val colNames: Array[String] = lines.next().map(_.trim)

			val seqOfValidated = lines.map: lineStr =>
				extractor(using colNames.zip(lineStr.map(_.trim)).toMap)

			Validated.sequence(seqOfValidated)

		Validated.fromTry(tryRes).flatMap(identity)

	def parsePerson(using Lookup): Validated[TcPerson[A]] =
		for(
			fname <- lookUp(FirstNameCol).require("person must have first name");
			lname <- lookUp(LastNameCol).require("person must have last name");
			tcId <- lookUp(PersonIdCol).map(makeId).require("unique ATC's id is required for a person");
			email <- lookUp(EmailCol).optional;
			orcid <- lookUpOrcid(OrcidCol);
			cpId = CpVocab.getPersonCpId(fname, lname)
		) yield
			TcPerson(cpId, Some(tcId), fname, lname, email.map(_.toLowerCase), orcid)

	def lookUpDate(colName: String)(using Lookup): Validated[Option[Instant]] = {
		lookUp(colName).optional.map{dsOpt =>
			dsOpt.collect{case ds if ds != InfinitePast =>
				Instant.parse(ds.replace(' ', 'T') + "Z")
			}
		}
	}


	def readAllOrgs(instruments: Path, stations: Path): Validated[OrgsMap] = {
		for(
			vendors <- parseOrgs(instruments, InstrVendorIdCol, InstrVendorCol);
			owners <- parseOrgs(instruments, InstrOwnerIdCol, InstrOwnerCol);
			respOrgs <- parseOrgs(stations, StationInstIdCol, StationInstNameCol, Some(StationInstWeb))
		) yield (owners ++ vendors ++ respOrgs).toMap
	}

	private def parseOrgs(file: Path, idCol: String, nameCol: String, websiteCol: Option[String] = None) =
		parseFromCsv(file){
			val demand = lookUpMandatory(instrumentsTbl) _
			for(
				idStr <- demand(idCol) if idStr != undefinedOrgId;
				id = makeOrgId(idStr);
				name <- demand(nameCol);
				websiteOpt <- new Validated(websiteCol).flatMap(lookUp).map{s =>
					val uri = if(s.startsWith("http")) s else ("https://" + s)
					new URI(uri)
				}.optional
			) yield{

				val labelOpt = name match{
					case labelParen(lbl) => Some(lbl)
					case _ => None
				}

				val cpId = UriId.escaped(labelOpt.getOrElse(name))

				val nameFinal = labelOpt.fold(name)(lbl => name.replace(s" ($lbl)", ""))
				val org = Organization(
					self = UriResource(uri = dummyUri, label = labelOpt, comments = Nil),
					name = nameFinal,
					email = None,
					website = websiteOpt,
					webpageDetails = None
				)

				id -> TcGenericOrg[A](cpId, Some(id), org)
			}
		}

	def parseInstruments(instruments: Path, orgs: OrgsMap): Validated[Seq[TcInstrument[A]]] = {
		parseFromCsv(instruments){
			val demand = lookUpMandatory(instrumentsTbl) _
			for(
				id <- demand(InstrIdCol).map(makeId);
				nameOpt <- lookUp(InstrNameCol).optional;
				serial <- lookUp(InstrSerialCol).orElse(TcMetaSource.defaultSerialNum);
				vendorId <- demand(InstrVendorIdCol).map(makeOrgId);
				ownerId <- demand(InstrOwnerIdCol).map(makeOrgId);
				model <- demand(InstrModelCol);
				related <- lookUp(InstrRelatedCol).flatMap(parseRelatedInstrs).orElse(Nil)
			) yield TcInstrument(
				tcId = id,
				sn = serial,
				model = model,
				vendor = orgs.get(vendorId),
				name = nameOpt,
				owner = orgs.get(ownerId),
				partsCpIds = related,
				deployments = Nil
			)
		}
	}

	private def parseRelatedInstrs(list: String): Validated[Seq[UriId]] = Validated{
		list.split(",").map{idStr =>
			TcConf.tcScopedId(UriId.escaped(idStr.trim))(using AtcConf)
		}.toIndexedSeq
	}

	private val labelParen = raw".*\(([A-Z]\w+)\).*".r
}
