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

	override def readState: Validated[State] =
		for(stations <- parseStations(getTableFile(stationsId)))
			yield new TcState(stations, Nil, Nil)
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
		5  -> Some(PI), //Station supervising PI
		4  -> Some(Engineer), //Tank configurator
		12 -> Some(PI), //Species PI
		9  -> None //Other station contact
	)

	def parseRow(line: String): Array[String] = line.split(';').map(_.trim)

	def parseCountry(s: String): Option[CountryCode] = CountryCode.unapply(countryMap.getOrElse(s.trim.toLowerCase, s.trim))

	def parseStations(path: Path): Validated[IndexedSeq[TcStationaryStation[A]]] = {
		val stationLines = scala.io.Source.fromFile(path.toFile).getLines()
		val colNames = parseRow(stationLines.next())

		val Seq(tcIdIdx, stIdIdx, stNameIdx, countryIdx, latIdx, lonIdx, altIdx) = Seq(
			IdCol, StationIdCol, StationNameCol, CountryCol, LatCol, LonCol, AltCol
		).map(colName => colNames.indexOf(colName))

		stationLines.map{line =>
			val r = parseRow(line)
			val stId = r(stIdIdx)
			val pos = Position(r(latIdx).toDouble, r(lonIdx).toDouble, Some(r(altIdx).toFloat))
			TcStationaryStation[A](
				cpId = TcConf.stationId[A](stId),
				tcId = TcConf.AtcConf.makeId(r(tcIdIdx)),
				id = stId,
				pos = pos,
				name = r(stNameIdx),
				country = parseCountry(r(countryIdx))
			)
		}.toIndexedSeq

		???
	}

	def parseMemberships(
		contacts: Path, roles: Path,
		stations: Seq[TcStation[A]]
	): Validated[IndexedSeq[Membership[A]]] = {

		val stationLookup = stations.map(s => s.tcId -> s).toMap

		val peopleLookupVal = parseFromCsv(contacts)(parsePerson(_)).map{ppl =>
			(for(pers <- ppl; id <- pers.tcIdOpt) yield id -> pers).toMap
		}

		parseFromCsv[Membership[A]](roles){implicit row =>
			def notFoundMsg(col: String) = s"$col not found in roles table on row ${row.mkString(", ")}"

			for(
				peopleLookup <- peopleLookupVal;
				stationIdStr <- lookUp(RoleStationIdCol).require(notFoundMsg(RoleStationIdCol));
				station <- new Validated(stationLookup.get(makeId(stationIdStr))).require(s"Could not find station with internal ATC id $stationIdStr");
				persIdStr <- lookUp(RolePersIdCol).require(notFoundMsg(RolePersIdCol));
				person <- new Validated(peopleLookup.get(makeId(persIdStr))).require(s"Could not find person with internal ATC id persIdStr");
				roleId <- lookUp(RoleIdCol).map(_.toInt).require("Problem parsing role id");
				role <- new Validated(roleMap.get(roleId).flatten)
			) yield {
				val assumedRole = new AssumedRole[A](role, person, station, None)
				Membership("", assumedRole, None, None)
			}
		}
		//val Seq(persIdIdx, fnIdx, lnIdx, persEmailIdx, orcidIdx)
		???
	}

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
			Person(cpId, Some(makeId(tcId)), fname, lname, email)

	def parseStationRoles(peopleLookup: Map[TcId[A], Person[A]]): Validated[IndexedSeq[Membership[A]]] = {

		???
	}
}
