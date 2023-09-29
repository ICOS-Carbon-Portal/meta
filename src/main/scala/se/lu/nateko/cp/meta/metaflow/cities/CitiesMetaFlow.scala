package se.lu.nateko.cp.meta.metaflow.cities

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.CitiesMetaFlowConfig
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.core.data.CountryCode
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.MetaUploadConf

object CitiesMetaFlow:
	def init(
		db: MetaDb, flowConf: CitiesMetaFlowConfig
	)(using Materializer, ActorSystem, EnvriConfigs): MetaFlow =

		given Envri = Envri.ICOSCities

		val diff = StateDiffApplier(db, flowConf, summon[ActorSystem].log)

		def startFlow[TC <: CitiesTC : TcConf](uploadConf: MetaUploadConf, cc: CountryCode): MetaFlow =
			val ms = MidLowCostMetaSource[TC](uploadConf, cc)
			MetaFlow(Seq(ms), ms.state.to(Sink.foreach(diff.apply[TC])).run())

		val Seq(de,fr,ch) = Seq("DE", "FR", "CH").flatMap(CountryCode.unapply)

		MetaFlow.join(
			Seq(
				startFlow[MunichMidLow.type](flowConf.munichUpload, de),
				startFlow[ParisMidLow.type](flowConf.parisUpload, fr),
				startFlow[ZurichMidLow.type](flowConf.zurichUpload, ch)
			)
		)
	end init
end CitiesMetaFlow
