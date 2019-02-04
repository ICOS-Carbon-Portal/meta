package se.lu.nateko.cp.meta.icos

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorAttributes
import akka.stream.Materializer
import akka.stream.Supervision
import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.EtcUploadConfig
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.etcupload.StationId
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry
import se.lu.nateko.cp.meta.ingestion.badm.BadmNumericValue
import se.lu.nateko.cp.meta.ingestion.badm.BadmStringValue
import se.lu.nateko.cp.meta.ingestion.badm.BadmValue
import se.lu.nateko.cp.meta.ingestion.badm.EtcEntriesFetcher
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.urlEncode

class EtcMetaSource(conf: EtcUploadConfig)(
	implicit system: ActorSystem, mat: Materializer, tcConf: TcConf[ETC.type]
) extends TcMetaSource[ETC.type] {
	import EtcMetaSource._
	import system.dispatcher

	def state: Source[State, Cancellable] = Source
		.tick(25.seconds, 5.hours, NotUsed)
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


	def fetchFromEtc(): Future[Validated[State]] = Http()
		.singleRequest(HttpRequest(uri = conf.fileMetaService.toASCIIString))
		.flatMap(EtcEntriesFetcher.responseToJson)
		.map(Parser.parseEntriesFromEtcJson)
		.map(getState)

	def getState(badms: Seq[BadmEntry]): Validated[State] = {
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

	def getStation(stId: StationId, badms: Seq[BadmEntry]): Validated[Station] = {
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
//			siteId <- getString("GRP_HEADER/SITE_ID")
//				.require(s"Station $id had no value for SITE_ID");
			siteName <- getString("GRP_HEADER/SITE_NAME")
				.require(s"Station $id had no value for SITE_NAME");
			pos <- getPosition
				.require(s"Station $id had no properly specified geo-position");
			pi <- getPerson(toLookup(piEntries), tcConf)
				.require(s"Station $id had no properly specified PI")
				.require(_.email.isDefined, s"PI of station $id had no email");
			country <- getCountryCode(stId)
		) yield {
			//TODO Use an actual guaranteed-stable id as tcId here
			val cpStation = new CpStationaryStation(id, tcConf.makeId(id), siteName, id, Some(country), pos)
			new TcStation[ETC.type](cpStation, SinglePi(pi))
		}
	}
}

object EtcMetaSource{

	val StationId(falso) = "FA-Lso"
	type Lookup = Map[String, BadmValue]
	type EtcInstrument = Instrument[ETC.type]
	type EtcPerson = Person[ETC.type]

	def getInstruments(stId: StationId, badms: Seq[BadmEntry])(implicit tcConf: TcConf[ETC.type]): Seq[Validated[EtcInstrument]] = {
		val sid = stId.id
		badms.filter(_.variable == "GRP_LOGGER").map{badm =>
			implicit val lookup = toLookup(Seq(badm))
			for(
				lid <- getNumber("GRP_LOGGER/LOGGER_ID").require(s"a logger at $sid has no id");
				model <- getString("GRP_LOGGER/LOGGER_MODEL").require(s"a logger $lid at $sid has no model");
				sn <- lookUp("GRP_LOGGER/LOGGER_SN").require(s"a logger $lid at $sid has no serial number")
			) yield
				//TODO Use TC-stable station id as component of tcId
				Instrument(s"ETC_${sid}_$lid", tcConf.makeId(s"${sid}_$lid"), model, sn.valueStr)
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

	def getPerson(implicit lookup: Map[String, BadmValue], tcConf: TcConf[ETC.type]): Validated[EtcPerson] =
		for(
			name <- getString("GRP_TEAM/TEAM_MEMBER_NAME").require("person must have name");
			email <- getString("GRP_TEAM/TEAM_MEMBER_EMAIL").optional;
			(fname, lname) <- parseName(name)
		) yield
			//TODO Use proper stable TC id for the person here
			Person(urlEncode(fname + "_" + lname), tcConf.makeId(fname + "_" + lname), fname, lname, email)


	def parseName(name: String): Validated[(String, String)] = {
		val comps = name.trim.split("\\s+")
		if(comps.size <= 1) Validated.error("Names are expected to have more than one component")
		else Validated((comps(0), comps.drop(1).mkString(" ")))
	}

	def getCountryCode(stId: StationId): Validated[CountryCode] = getCountryCode(stId.id.take(2))

	def getCountryCode(s: String): Validated[CountryCode] = s match{
		case CountryCode(cc) => Validated(cc)
		case _ => Validated.error(s + " is not a valid country code")
	}
}
