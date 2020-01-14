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
			FileIO.toPath(file).mapMaterializedValue{f =>
				f.flatMap(ioRes => Future.fromTry(ioRes.status)).onComplete{
					case Success(Done) => listener ! 1
					case Failure(exc) => system.log.error(exc, "Error writing ATC metadata table")
				}
				f
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

	override def readState: Validated[State] = Validated{
		val stationLines = scala.io.Source.fromFile(getTableFile(stationsId).toFile).getLines()
		val colNames = parseRow(stationLines.next())

		val Seq(tcIdIdx, stIdIdx, stNameIdx, countryIdx, latIdx, lonIdx, altIdx) = Seq(
			IdCol, StationIdCol, StationNameCol, CountryCol, LatCol, LonCol, AltCol
		).map(colNames.indexOf)

		val stations = stationLines.map{line =>
			val r = parseRow(line)
			val stId = r(stIdIdx)
			val pos = Position(r(latIdx).toDouble, r(lonIdx).toDouble, Some(r(altIdx).toFloat))
			TcStationaryStation[ATC.type](
				cpId = stationId(stId),
				tcId = TcConf.AtcConf.makeId(r(tcIdIdx)),
				id = stId,
				pos = pos,
				name = r(stNameIdx),
				country = parseCountry(r(countryIdx))
			)
		}.toIndexedSeq

		new TcState(stations, Nil, Nil)
	}
}

object AtcMetaSource{
	val StorageDir = "atcmeta"
	val stationsId = "stations"

	val IdCol = "#Id"
	val StationIdCol = "ShortName"
	val StationNameCol = "FullName"
	val CountryCol = "Country"
	val LatCol = "Latitude"
	val LonCol = "Longitude"
	val AltCol = "EAS"

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

	def parseRow(line: String): Array[String] = line.split(';').map(_.trim)

	def parseCountry(s: String): Option[CountryCode] = CountryCode.unapply(countryMap.getOrElse(s.trim.toLowerCase, s.trim))
}
