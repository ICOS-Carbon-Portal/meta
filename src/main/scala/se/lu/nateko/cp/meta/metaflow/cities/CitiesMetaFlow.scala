package se.lu.nateko.cp.meta.metaflow.cities

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.{AtcStationSpecifics, CityNetwork, CountryCode, EnvriConfigs, IcosCitiesStationSpecifics}
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.metaflow.icos.{ATC, AtcConf, AtcMetaSource}
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.{CitiesMetaFlowConfig, MetaDb, MetaUploadConf}

object CitiesMetaFlow:
	def init(
		db: MetaDb, flowConf: CitiesMetaFlowConfig
	)(using Materializer, ActorSystem, EnvriConfigs): MetaFlow =

		given Envri = Envri.ICOSCities

		val diff = StateDiffApplier(db, flowConf)

		def startFlow[TC <: CitiesTC : TcConf](uploadConf: MetaUploadConf, cc: CountryCode): MetaFlow =
			val ms = MidLowCostMetaSource[TC](uploadConf, cc)
			MetaFlow(Seq(ms), ms.state.to(Sink.foreach(diff.apply[TC])).run())

		val Seq(de,fr,ch) = Seq("DE", "FR", "CH").flatMap(CountryCode.unapply)

		val atcSource = AtcMetaSource(flowConf.atcUpload)

		MetaFlow.join(
			Seq(
				startFlow[MunichMidLow.type](flowConf.munichUpload, de),
				startFlow[ParisMidLow.type](flowConf.parisUpload, fr),
				startFlow[ZurichMidLow.type](flowConf.zurichUpload, ch),
				MetaFlow(Seq(atcSource), atcSource.state.map(injectNetworkInfo).to(Sink.foreach(diff.apply[ATC.type])).run())
			)
		)
	end init

	def injectNetworkInfo(state: TcState[ATC.type]): TcState[ATC.type] =
		val stations = state.stations.map: s =>
			val citySpec = s.core.specificInfo match
				case atc: AtcStationSpecifics => IcosCitiesStationSpecifics(atc.timeZoneOffset, CityNetwork.Paris)
				case _ => throw MetadataException("Unexpected station-specific info, must be AtcStationSpecifics")
			s.copy(core = s.core.copy(specificInfo = citySpec))
		TcState(stations, state.roles, state.instruments)

end CitiesMetaFlow
