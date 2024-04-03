package se.lu.nateko.cp.meta.metaflow.icos

import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import se.lu.nateko.cp.meta.EtcConfig
import se.lu.nateko.cp.meta.IcosMetaFlowConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.metaflow.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.cpauth.core.UserId
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfigs


object IcosMetaFlow:

	def init(
		db: MetaDb, etcConf: EtcConfig, flowConf: IcosMetaFlowConfig
	)(using Materializer, ActorSystem, EnvriConfigs): MetaFlow =

		given Envri = Envri.ICOS
		val log = summon[ActorSystem].log

		val diff = StateDiffApplier(db, flowConf, log)

		def startFlow[TC <: IcosTC: TcConf](src: TcMetaSource[TC]): () => Unit =
			src.state.to(Sink.foreach(diff.apply[TC])).run()

		val etcRun =
			if etcConf.ingestFileMeta then
				startFlow(EtcMetaSource(etcConf, db.vocab))
			else () => ()

		val otcRun = db.instanceServers.get(flowConf.otcMetaInstanceServerId) match
			case None => () => ()
			case Some(otcServer: WriteNotifyingInstanceServer) =>
				val sparql = Rdf4jSparqlRunner(db.vanillaRepo)
				startFlow(OtcMetaSource(otcServer, sparql, log))
			case _ => throw Exception:
				"Configuration problem! OTC metadata-entry instance server is supposed to be a notifying one."

		val atcSource = AtcMetaSource(flowConf.atcUpload)

		val cancellers = Seq(startFlow(atcSource), etcRun, otcRun)

		MetaFlow(Seq(atcSource), () => cancellers.foreach(_.apply()))
	end init
end IcosMetaFlow
