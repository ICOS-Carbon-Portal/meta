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
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.core.data.Position
import java.io.File

import EtcMetaSource.{Lookup, lookUp}
import se.lu.nateko.cp.meta.services.CpVocab
import java.time.Instant

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
	val CountryCol = "Country"
	val LatCol = "Latitude"
	val LonCol = "Longitude"
	val AltCol = "EAS"

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

	def parseRow(line: String): Array[String] = line.split(';').map(_.trim)

	def parseCountry(s: String): Option[CountryCode] = CountryCode.unapply(countryMap.getOrElse(s.trim.toLowerCase, s.trim))

	def parseStations(path: Path): Validated[IndexedSeq[TcStationaryStation[A]]] = parseFromCsv(path){implicit row =>
		val demand = lookUpMandatory("stations") _

		for(
			stIdStr <- demand(StationIdCol);
			tcId <- demand(IdCol);
			lat <- demand(LatCol).map(_.toDouble);
			lon <- demand(LonCol).map(_.toDouble);
			alt <- demand(AltCol).map(_.toFloat);
			name <- demand(StationNameCol);
			country <- demand(CountryCol).map(parseCountry)
		) yield TcStationaryStation[A](
			cpId = TcConf.stationId[A](stIdStr),
			tcId = TcConf.AtcConf.makeId(tcId),
			id = stIdStr,
			pos = Position(lat, lon, Some(alt)),
			name = name,
			country = country
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
			def notFoundMsg(col: String) = s"$col not found in roles table on row ${row.mkString(", ")}"
			val demand = lookUpMandatory("roles") _

			for(
				peopleLookup <- peopleLookupVal;
				stationIdStr <- demand(RoleStationIdCol);
				station <- new Validated(stationLookup.get(makeId(stationIdStr))).require(s"Could not find station with internal ATC id $stationIdStr");
				persIdStr <- demand(RolePersIdCol);
				person <- new Validated(peopleLookup.get(makeId(persIdStr))).require(s"Could not find person with internal ATC id persIdStr");
				roleId <- lookUp(RoleIdCol).map(_.toInt).require("Problem parsing role id");
				roleOpt <- new Validated(roleMap.get(roleId)).require(s"Unknown ATC role with id $roleId");
				role <- new Validated(roleOpt);
				startDate <- lookUpDate(RoleStartCol);
				endDate <- lookUpDate(RoleEndCol)
			) yield {
				val assumedRole = new AssumedRole[A](role, person, station, None)
				Membership("", assumedRole, startDate, endDate)
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
			cpId = CpVocab.getPersonCpId(fname, lname)
		) yield
			Person(cpId, Some(makeId(tcId)), fname, lname, email.map(_.toLowerCase))

	def lookUpDate(colName: String)(implicit row: Lookup): Validated[Option[Instant]] = {
		lookUp(colName).optional.map{dsOpt =>
			dsOpt.map{ds =>
				Instant.parse(ds.replace(' ', 'T') + "Z")
			}
		}
	}
}
