package se.lu.nateko.cp.meta.icos

import scala.util.Success
import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.MetaFlowConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.cpauth.core.UserId
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfigs


class MetaFlow(val atcSourceOpt: Option[AtcMetaSource], val cancel: () => Unit)

object MetaFlow:

	def initiate(db: MetaDb, conf: CpmetaConfig)(using Materializer, ActorSystem): Try[MetaFlow] =
		conf.instanceServers.metaFlow match
			case None => Success(new MetaFlow(None, () => ()))
			case Some(flowConf) => Try(init(db, conf, flowConf))

	private def init(
		db: MetaDb, conf: CpmetaConfig, flowConf: MetaFlowConfig
	)(using Materializer, ActorSystem): MetaFlow =

		given EnvriConfigs = conf.core.envriConfigs

		val vf = db.repo.getValueFactory
		val log = summon[ActorSystem].log

		val vocab = new CpVocab(vf)
		val meta = new CpmetaVocab(vf)
		val rdfMaker = new RdfMaker(vocab, meta)

		val cpServer = db.instanceServers(flowConf.cpMetaInstanceServerId)
		val icosServer = db.instanceServers(flowConf.icosMetaInstanceServerId)

		val otcServer = db.instanceServers(flowConf.otcMetaInstanceServerId) match
			case wnis: WriteNotifyingInstanceServer => wnis
			case _ => throw new Exception(
				"Configuration problem! OTC metadata-entry instance server is supposed to be a notifying one."
			)

		val rdfReader = {
			val plainFetcher = db.uploadService.servers.metaFetchers(Envri.ICOS).plainObjFetcher
			new RdfReader(cpServer, icosServer, plainFetcher)
		}

		val diffCalc = new RdfDiffCalc(rdfMaker, rdfReader)

		val sparql = new Rdf4jSparqlRunner(db.repo)

		val atcSource = new AtcMetaSource(UserId(flowConf.atcMetaUploadUser))
		val otcSource = new OtcMetaSource(otcServer, sparql, log)
		val etcSource = new EtcMetaSource(conf.dataUploadService.etc, vocab)

		def applyDiff[T <: TC : TcConf](tip: String)(state: TcState[T]): Unit = {
			val diffV = diffCalc.calcDiff(state)
			if diffV.errors.nonEmpty then
				val nUpdates = diffV.result.fold(0)(_.size)
				val errors = diffV.errors.distinct.mkString("\n")
				log.warning(s"Error(s) calculating RDF diff (got $nUpdates updates) for $tip metadata:\n$errors")
			for updates <- diffV do
				icosServer.applyAll(updates)().fold(
					err => log.error(err, s"Problem applying $tip station-metadata diff"),
					_ => log.info(s"Calculated and applied $tip station-metadata diff (${updates.size} RDF changes)")
				)
		}

		val stopAtc = atcSource.state.map{applyDiff[ATC.type]("ATC")}.to(Sink.ignore).run()
		val stopOtc = otcSource.state.map{applyDiff[OTC.type]("OTC")}.to(Sink.ignore).run()
		val stopEtc = etcSource.state.map{applyDiff[ETC.type]("ETC")}.to(Sink.ignore).run()
		new MetaFlow(Some(atcSource),
			() => {
				stopAtc()
				stopOtc()
				stopEtc.cancel()
			}
		)
	end init
end MetaFlow
