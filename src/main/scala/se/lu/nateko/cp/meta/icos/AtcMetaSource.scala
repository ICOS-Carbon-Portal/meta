package se.lu.nateko.cp.meta.icos

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try
import scala.util.Success

import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Status
import akka.Done
import akka.stream.IOResult
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.core.{data => core}
import core.{CountryCode, Position, Orcid}
import java.io.File

import EtcMetaSource.{Lookup, lookUp, lookUpOrcid}
import se.lu.nateko.cp.meta.services.CpVocab
import java.time.Instant
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.core.data.IcosStationClass
import java.time.LocalDate

class AtcMetaSource(allowedUser: UserId)(implicit system: ActorSystem) extends TriggeredMetaSource[ATC.type] {
	import AtcMetaSource._
	import system.dispatcher

	def log = system.log
	private[this] var listener: ActorRef = system.deadLetters

	override def registerListener(actor: ActorRef): Unit = {
		if(listener != system.deadLetters) listener ! Status.Success
		listener = actor
	}

	def getTableSink(tableId: String, user: UserId): Try[Sink[ByteString, Future[IOResult]]] = {

		if(user == allowedUser) Try{
			val file = getTableFile(tableId)
			FileIO.toPath(file).mapMaterializedValue{_
				.andThen{
					case Success(_) => listener ! 1
					case Failure(exc) => system.log.error(exc, "Error writing ATC metadata table")
				}
			}
		} else
			Failure(new UnauthorizedUploadException(s"Only $allowedUser is allowed to upload ATC metadata to CP"))
	}

	def getDirectory(): Path = {
		val dir = Paths.get("atcmeta").toAbsolutePath
		Files.createDirectories(dir)
	}

	def getTableFile(tableId: String): Path = {
		getDirectory().resolve(tableId)
	}

	override def readState: Validated[State] = for(
			stations <- parseStations(getTableFile(stationsId));
			//TODO Add instruments
			membs <- parseMemberships(getTableFile("contacts"), getTableFile("roles"), stations)
		) yield
			new TcState(stations, membs, Nil)
}

object AtcMetaSource{
	type A = ATC.type
	import TcConf.AtcConf.makeId

	val StorageDir = "atcmeta"
	val stationsId = "stations"

	val IdCol = "#Id"
	val StationIdCol = "ShortName"
	val StationNameCol = "FullName"
	val StationClassCol = "Class"
	val CountryCol = "Country"
	val LatCol = "Latitude"
	val LonCol = "Longitude"
	val AltCol = "EAS"
	val LabelingDateCol = "LabelingDate"

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

	private val countryMap = Map(
		"belgium"         -> "BE",
		"great britain"   -> "GB",
		"italy"           -> "IT",
		"ireland"         -> "IE",
		"germany"         -> "DE",
		"denmark"         -> "DK",
		"poland"          -> "PL",
		"sweden"          -> "SE",
		"switzerland"     -> "CH",
		"spain"           -> "ES",
		"czech republic"  -> "CZ",
		"the netherlands" -> "NL",
		"france"          -> "FR",
		"finland"         -> "FI",
		"norway"          -> "NO"
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

	def parseRow(line: String): Array[String] = line.split(';').map(_.trim)

	def parseCountryCode(s: String): Validated[CountryCode] = {
		val ccOpt = CountryCode.unapply(countryMap.getOrElse(s.trim.toLowerCase, s.trim))
		val errors = if(ccOpt.isEmpty) Seq(s"Neither a recognized country (in AtcMetaSource) nor a country code: $s") else Nil
		new Validated(ccOpt, errors)
	}

	def parseLocalDate(ts: String): Validated[LocalDate] = Validated(LocalDate.parse(ts.take(10)))

	def parseStationClass(s: String): Validated[IcosStationClass.Value] = Validated.fromTry(IcosStationClass.parse(s.trim))

	def parseStations(path: Path): Validated[IndexedSeq[TcStation[A]]] = parseFromCsv(path){implicit row =>
		val demand = lookUpMandatory(stationsId) _

		for(
			stIdStr <- demand(StationIdCol);
			tcId <- demand(IdCol);
			lat <- demand(LatCol).map(_.toDouble);
			lon <- demand(LonCol).map(_.toDouble);
			alt <- demand(AltCol).map(_.toFloat);
			stClass <- demand(StationClassCol).flatMap(parseStationClass).optional;
			name <- demand(StationNameCol);
			country <- demand(CountryCol).flatMap(parseCountryCode).optional;
			lblDate <- demand(LabelingDateCol).flatMap(parseLocalDate).optional
		) yield TcStation[A](
			cpId = TcConf.stationId[A](UriId.escaped(stIdStr)),
			tcId = TcConf.AtcConf.makeId(tcId),
			core = Station(
				org = core.Organization(
					self = core.UriResource(uri = EtcMetaSource.dummyUri, label = Some(stIdStr), comments = Nil),
					name = name,
					email = None,
					website = None
				),
				id = stIdStr,
				coverage = Some(Position(lat, lon, Some(alt))),
				//TODO Support the responsible org for ATC here
				responsibleOrganization = None,
				pictures = Nil,
				specificInfo = core.PlainIcosSpecifics(
					stationClass = stClass,
					countryCode = country,
					labelingDate = lblDate
				)
			)
		)
	}

	def parseMemberships(
		contacts: Path, roles: Path,
		stations: Seq[TcStation[A]]
	): Validated[IndexedSeq[Membership[A]]] = {

		val stationLookup = stations.map(s => s.tcId -> s).toMap

		val peopleLookupVal = parseFromCsv(contacts)(parsePerson(_)).map{ppl =>
			(for(pers <- ppl; id <- pers.tcIdOpt) yield id -> pers).toMap
		}

		parseFromCsv(roles){implicit row =>
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

	def lookUpMandatory(tableName: String)(varName: String)(implicit row: Lookup): Validated[String] =
		lookUp(varName).require(s"$varName not found in $tableName table on row ${row.mkString(", ")}")

	def parseFromCsv[T](path: Path)(extractor: Lookup => Validated[T]): Validated[IndexedSeq[T]] = Validated{

		val lines = scala.io.Source.fromFile(path.toFile).getLines()

		val colNames: Array[String] = lines.next().split(';').map(_.trim)

		val seqOfValidated = lines.map{lineStr =>
			extractor(colNames.zip(lineStr.split(';').map(_.trim)).toMap)
		}

		Validated.sequence(seqOfValidated)
	}.flatMap(identity)

	def parsePerson(implicit row: Lookup): Validated[Person[A]] =
		for(
			fname <- lookUp(FirstNameCol).require("person must have first name");
			lname <- lookUp(LastNameCol).require("person must have last name");
			tcId <- lookUp(PersonIdCol).require("unique ATC's id is required for a person");
			email <- lookUp(EmailCol).optional;
			orcid <- lookUpOrcid(OrcidCol);
			cpId = CpVocab.getPersonCpId(fname, lname)
		) yield
			Person(cpId, Some(makeId(tcId)), fname, lname, email.map(_.toLowerCase), orcid)

	def lookUpDate(colName: String)(implicit row: Lookup): Validated[Option[Instant]] = {
		lookUp(colName).optional.map{dsOpt =>
			dsOpt.map{ds =>
				Instant.parse(ds.replace(' ', 'T') + "Z")
			}
		}
	}
}
