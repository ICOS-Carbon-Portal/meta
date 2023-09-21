package se.lu.nateko.cp.meta.metaflow

import akka.event.LoggingAdapter
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.CitiesMetaFlowConfig
import se.lu.nateko.cp.meta.IcosMetaFlowConfig
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.MetaFlowConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab


class StateDiffApplier(
	db: MetaDb, flowConf: MetaFlowConfig, log: LoggingAdapter
)(using EnvriConfigs, Envri):
	private val vf = db.repo.getValueFactory

	private val meta = new CpmetaVocab(vf)
	private val rdfMaker = new RdfMaker(db.vocab, meta)

	private val cpServer = db.instanceServers(flowConf.cpMetaInstanceServerId)
	private val envriServId = flowConf match
		case c: CitiesMetaFlowConfig => c.citiesMetaInstanceServerId
		case i: IcosMetaFlowConfig => i.icosMetaInstanceServerId
	private val envriServer = db.instanceServers(envriServId)


	private val rdfReader = {
		val plainFetcher = db.uploadService.servers.metaFetcher.get.plainObjFetcher
		new RdfReader(cpServer, envriServer, plainFetcher)
	}

	private val diffCalc = new RdfDiffCalc(rdfMaker, rdfReader)

	def apply[T <: TC](state: TcState[T])(using tcconf: TcConf[T]): Unit =
		val diffV = diffCalc.calcDiff(state)
		inline def tip = tcconf.tcPrefix
		if diffV.errors.nonEmpty then
			val nUpdates = diffV.result.fold(0)(_.size)
			val errors = diffV.errors.distinct.mkString("\n")
			log.warning(s"Error(s) calculating RDF diff (got $nUpdates updates) for $tip metadata:\n$errors")
		for updates <- diffV do
			envriServer.applyAll(updates)().fold(
				err => log.error(err, s"Problem applying $tip station-metadata diff"),
				_ => log.info(s"Calculated and applied $tip station-metadata diff (${updates.size} RDF changes)")
			)

end StateDiffApplier

