package se.lu.nateko.cp.meta.icos

import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.MetaDb
//import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab


object MetaFlow {

	def initiate(db: MetaDb, conf: CpmetaConfig)(implicit mat: Materializer, system: ActorSystem): Try[() => Unit] = Try{

		implicit val envriConfs = conf.core.envriConfigs
		

		val vf = db.repo.getValueFactory

		val vocab = new CpVocab(vf)
		val meta = new CpmetaVocab(vf)
		val rdfMaker = new RdfMaker(vocab, meta)

		val isConf = conf.instanceServers
		val cpServer = db.instanceServers(isConf.cpMetaInstanceServerId)
		val icosServer = db.instanceServers(isConf.icosMetaInstanceServerId)

//		val otcServer = db.instanceServers(isConf.otcMetaInstanceServerId) match{
//			case wnis: WriteNotifyingInstanceServer => wnis
//			case _ => throw new Exception(
//				"Configuration problem! OTC metadata-entry instance server is supposed to be a notifying one."
//			)
//		}

		val rdfReader = new RdfReader(cpServer, icosServer)

		val diffCalc = new RdfDiffCalc(rdfMaker, rdfReader)

//		val otcSource = new OtcMetaSource(otcServer, system.log)
		val etcSource = new EtcMetaSource(conf.dataUploadService.etc)

		def applyDiff[T <: TC : TcConf](tip: String)(state: TcState[T]): Unit = {
			val diffV = diffCalc.calcDiff(state)
			if(diffV.errors.isEmpty) diffV.foreach(icosServer.applyAll)
			else{
				system.log.warning(s"Error calculating RDF diff for $tip metadata:\n${diffV.errors.mkString("\n")}")
			}
		}

//		val stopOtc = otcSource.state.map{applyDiff("OTC")}.to(Sink.ignore).run()
		val stopEtc = etcSource.state.map{applyDiff("ETC")}.to(Sink.ignore).run()
		() => {
//			stopOtc()
			stopEtc.cancel()
		}
	}
}
