package se.lu.nateko.cp.meta.icos

import scala.util.Try

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.instanceserver.WriteNotifyingInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.core.data.Envri


class MetaFlow(val atcSource: AtcMetaSource, val cancel: () => Unit)

object MetaFlow {

	def initiate(db: MetaDb, conf: CpmetaConfig)(implicit mat: Materializer, system: ActorSystem): Try[MetaFlow] = Try{

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

		val rdfReader = {
			val plainFetcher = db.uploadService.servers.metaFetchers(Envri.ICOS).plainObjFetcher
			new RdfReader(cpServer, icosServer, plainFetcher)
		}

		val diffCalc = new RdfDiffCalc(rdfMaker, rdfReader)

		val sparql = new Rdf4jSparqlRunner(db.repo)

		//TODO Add ATC user to config
		val atcSource = new AtcMetaSource(UserId("uploader@ATC"))
		val otcSource = new OtcMetaSource(otcServer, sparql, system.log)
		val etcSource = new EtcMetaSource(conf.dataUploadService.etc)

		def applyDiff[T <: TC : TcConf](tip: String)(state: TcState[T]): Unit = {
			val diffV = diffCalc.calcDiff(state)
			if(diffV.errors.isEmpty) {
				diffV.foreach{updates =>
					icosServer.applyAll(updates)
					system.log.info(s"Calculated and applied $tip station-metadata diff (${updates.size} RDF changes)")
				}
			} else{
				val nUpdates = diffV.result.fold(0)(_.size)
				val errors = diffV.errors.mkString("\n")
				system.log.warning(s"Error(s) calculating RDF diff (got $nUpdates updates) for $tip metadata:\n$errors")
			}
		}

		val stopAtc = atcSource.state.map{applyDiff("ATC")}.to(Sink.ignore).run()
		val stopOtc = otcSource.state.map{applyDiff("OTC")}.to(Sink.ignore).run()
		val stopEtc = etcSource.state.map{applyDiff("ETC")}.to(Sink.ignore).run()
		new MetaFlow(atcSource,
			() => {
				stopAtc()
				stopOtc()
				stopEtc.cancel()
			}
		)
	}
}
