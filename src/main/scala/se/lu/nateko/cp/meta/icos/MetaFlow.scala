package se.lu.nateko.cp.meta.icos

import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.CpmetaConfig
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import scala.util.Try
import akka.NotUsed
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Sink


object MetaFlow {

	def initiate(db: MetaDb, conf: CpmetaConfig)(implicit mat: Materializer, system: ActorSystem): Try[NotUsed] = Try{

		implicit val envriConfs = conf.core.envriConfigs
		

		val vf = db.repo.getValueFactory

		val vocab = new CpVocab(vf)
		val meta = new CpmetaVocab(vf)
		val rdfMaker = new RdfMaker(vocab, meta)

		val isConf = conf.instanceServers
		val cpServer = db.instanceServers(isConf.cpMetaInstanceServerId)
		val icosServer = db.instanceServers(isConf.icosMetaInstanceServerId)

		val otcServer = db.instanceServers(isConf.otcMetaInstanceServerId) match{
			case wnis: WriteNotifyingInstanceServer => wnis
			case _ => throw new Exception(
				"Configuration problem! OTC metadata-entry instance server is supposed to be a notifying one."
			)
		}

		val rdfReader = new RdfReader(cpServer, icosServer)

		val diffCalc = new RdfDiffCalc(rdfMaker, rdfReader)

		val otcUpdates: Source[Any, Any] = Source.actorRef(1, OverflowStrategy.dropHead).mapMaterializedValue{actor =>
			otcServer.setSubscriber(() => actor ! NotUsed)
		}
		val otcSource = new OtcMetaSource(otcServer.inner, otcUpdates, system.log)

		//TODO Make sure the stream is restarted upon failures
		otcSource.state.map(s => icosServer.applyAll(diffCalc.calcDiff(s))).runWith(Sink.ignore)
		NotUsed
	}
}