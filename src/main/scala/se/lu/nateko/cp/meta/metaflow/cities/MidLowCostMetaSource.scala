package se.lu.nateko.cp.meta.metaflow.cities

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import se.lu.nateko.cp.meta.MetaUploadConf
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.metaflow.icos.AtcMetaSource.{lookUpMandatory, parseFromCsv}
import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource.{Lookup, dummyUri}
import se.lu.nateko.cp.meta.utils.Validated

class MidLowCostMetaSource[T <: CitiesTC : TcConf](
	conf: MetaUploadConf,
	countryCode: CountryCode
)(using ActorSystem) extends FileDropMetaSource[T](conf):
	import MidLowCostMetaSource.*

	override def readState: Validated[State] =
		for sites <- parseFromCsv(getTableFile(StationsTableName))(parseStation(countryCode))
		yield TcState(sites, Nil, Nil)


object MidLowCostMetaSource:
	val StationsTableName = "sites"
	val StationIdCol = "site"
	val StationNameCol = "site_name"
	val LatCol = "latitude"
	val LonCol = "longitude"
	val AltCol = "elevation"

	extension(s: String)
		def commaToDot: String = s.replace(',', '.')

	def parseStation[T <: CitiesTC : TcConf](countryCode: CountryCode)(using Lookup): Validated[TcStation[T]] =
		val demand = lookUpMandatory(StationsTableName) _
		for(
			stIdStr <- demand(StationIdCol);
			lat <- demand(LatCol).map(_.commaToDot.toDouble);
			lon <- demand(LonCol).map(_.commaToDot.toDouble);
			alt <- demand(AltCol).map(_.commaToDot.toFloat);
			name <- demand(StationNameCol)
		) yield TcStation[T](
			cpId = TcConf.stationId[T](UriId.escaped(stIdStr)),
			tcId = summon[TcConf[T]].makeId(stIdStr),
			core = Station(
				org = Organization(
					self = UriResource(uri = dummyUri, label = Some(stIdStr), comments = Nil),
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
				countryCode = Some(countryCode),
				specificInfo = IcosCitiesStationSpecifics(None, summon[TcConf[T]].tc.network),
				funding = None
			),
			responsibleOrg = None,
			funding = Nil
		)
	end parseStation

end MidLowCostMetaSource
