package se.lu.nateko.cp.meta.icos

import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.EtcUploadConfig
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry
import se.lu.nateko.cp.meta.ingestion.badm.BadmValue
import se.lu.nateko.cp.meta.ingestion.badm.EtcEntriesFetcher
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import se.lu.nateko.cp.meta.ingestion.badm.BadmNumericValue
import se.lu.nateko.cp.meta.ingestion.badm.BadmStringValue
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.urlEncode

class EtcMetaSource(conf: EtcUploadConfig)(implicit system: ActorSystem, mat: Materializer) extends TcMetaSource {
	import system.dispatcher
	import EtcMetaSource._

	type Pis = SinglePi

	def state: Source[TcState, Any] = {
		???
	}

	def fetchFromEtc(): Future[Validated[TcState]] = Http()
		.singleRequest(HttpRequest(uri = conf.fileMetaService.toASCIIString))
		.flatMap(EtcEntriesFetcher.responseToJson)
		.map(Parser.parseEntriesFromEtcJson)
		.map(getState)

	def getState(badms: Seq[BadmEntry]): Validated[TcState] = {
		val stationBadms = badms.groupBy(_.stationId).toSeq.collect{
			case (Some(id), badms) if id != falso => id -> badms
		}
		val stationsV = stationBadms.map{case (id, badms) => getStation(id, badms)}
		val instrumentsV = stationBadms.flatMap{case (id, badms) => getInstruments(id, badms)}
		for(
			stations <- Validated.sequence(stationsV);
			instruments <- Validated.sequence(instrumentsV)
		) yield new TcState(stations, Nil, instruments)
	}

	def getStation(stId: StationId, badms: Seq[BadmEntry]): Validated[TcStation] = {
		val id = stId.id
		val (roleEntries, nonRoleEntries) = badms.partition(_.variable == "GRP_TEAM")

		val (piEntries, nonPiEntries) = roleEntries.partition(be =>
			be.values.exists(bv => bv.variable == "TEAM_MEMBER_ROLE" && bv.valueStr == "PI")
		)

		val nPisValidation = if(piEntries.size <= 1) Validated(())
			else Validated.error(s"ETC stations must have exactly one PI but $id had ${piEntries.size}").optional

		val nonPiRolesValidation = if(nonPiEntries.isEmpty) Validated(())
			else Validated.error(s"Encountered non-PI ETC role(s) for $id." +
				" They were ignored for now (support must be added by CP)").optional

		implicit val lookup = toLookup(nonRoleEntries)
		for(
			_ <- nPisValidation;
			_ <- nonPiRolesValidation;
			siteId <- getString("GRP_HEADER/SITE_ID").require(s"Station $id had no value for SITE_ID");
			siteName <- getString("GRP_HEADER/SITE_NAME").require(s"Station $id had no value for SITE_NAME");
			pos <- getPosition.require(s"Station $id had no properly specified geo-position");
			pi <- getPerson(toLookup(piEntries)).require(s"Station $id had no properly specified PI")
		) yield
			TcStation("ES_" + siteId, siteId, siteName, pos, SinglePi(pi))
	}
}

object EtcMetaSource{

	val StationId(falso) = "FA-Lso"
	type Lookup = Map[String, BadmValue]

	def getInstruments(stId: StationId, badms: Seq[BadmEntry]): Seq[Validated[Instrument]] = {
		val sid = stId.id
		badms.filter(_.variable == "GRP_LOGGER").map{badm =>
			implicit val lookup = toLookup(Seq(badm))
			for(
				lid <- getNumber("GRP_LOGGER/LOGGER_ID").require(s"a logger at $sid has no id");
				model <- getString("GRP_LOGGER/LOGGER_MODEL").require(s"a logger $lid at $sid has no model");
				sn <- lookUp("GRP_LOGGER/LOGGER_SN").require(s"a logger $lid at $sid has no serial number")
			) yield
				Instrument(s"ETC_${sid}_$lid", model, Some(sn.valueStr))
		}
	}

	def lookUp(varName: String)(implicit lookup: Lookup): Validated[BadmValue] =
		new Validated(lookup.get(varName))

	def getNumber(varName: String)(implicit lookup: Lookup): Validated[Number] = lookUp(varName).flatMap{
		case BadmNumericValue(_, _, v) => Validated(v)
		case _ => Validated.error(s"$varName must have been a number")
	}

	def getString(varName: String)(implicit lookup: Lookup): Validated[String] = lookUp(varName).flatMap{
		case BadmStringValue(_, v) => Validated(v)
		case _ => Validated.error(s"$varName must have been a string")
	}

	def getPosition(implicit lookup: Lookup): Validated[Position] =
		for(
			lat <- getNumber("GRP_LOCATION/LOCATION_LAT").require("station must have latitude");
			lon <- getNumber("GRP_LOCATION/LOCATION_LONG").require("station must have longitude");
			alt <- getNumber("GRP_LOCATION/LOCATION_ELEV").optional
		) yield Position(lat.doubleValue, lon.doubleValue, alt.map(_.floatValue))

	def toLookup(badms: Seq[BadmEntry]): Lookup =
		badms.flatMap(be => be.values.map(bv => (be.variable + "/" + bv.variable) -> bv)).toMap

	def getPerson(implicit lookup: Map[String, BadmValue]): Validated[Person] =
		for(
			name <- getString("GRP_TEAM/TEAM_MEMBER_NAME");
			email <- getString("GRP_TEAM/TEAM_MEMBER_EMAIL").optional;
			(fname, lname) <- parseName(name)
		) yield
			Person(urlEncode(fname + "_" + lname), "", fname, lname, email)


	def parseName(name: String): Validated[(String, String)] = {
		val comps = name.trim.split("\\s+")
		if(comps.size <= 1) Validated.error("Names are expected to have more than one component")
		else Validated((comps(0), comps.drop(1).mkString(" ")))
	}
}
