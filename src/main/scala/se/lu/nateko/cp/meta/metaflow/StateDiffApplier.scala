package se.lu.nateko.cp.meta.metaflow

import eu.icoscp.envri.Envri
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.{CitiesMetaFlowConfig, IcosMetaFlowConfig, MetaDb, MetaFlowConfig}


class StateDiffApplier(
	db: MetaDb, flowConf: MetaFlowConfig
)(using EnvriConfigs, Envri):

	private val log = LoggerFactory.getLogger(getClass())
	private val rdfMaker = new RdfMaker(db.vocab, db.metaVocab)

	private val envriServId = flowConf match
		case c: CitiesMetaFlowConfig => c.citiesMetaInstanceServerId
		case i: IcosMetaFlowConfig => i.icosMetaInstanceServerId
	private val envriServer = db.instanceServers(envriServId)


	private val diffCalcV =
		for
			cpLens <- db.lenses.cpLens(flowConf)
			envriLens <- db.lenses.metaInstanceLens
			docLens <- db.lenses.documentLens
		yield
			val lenses = MetaflowLenses(cpLens, envriLens, docLens)
			val reader = RdfReader(db.metaReader, db.vanillaGlob, lenses)
			new RdfDiffCalc(rdfMaker, reader)

	def apply[T <: TC](state: TcState[T])(using tcconf: TcConf[T]): Unit =
		val diffV = diffCalcV.flatMap(_.calcDiff(state))
		inline def tip = tcconf.tcPrefix
		if diffV.errors.nonEmpty then
			val nUpdates = diffV.result.fold(0)(_.size)
			val errors = diffV.errors.distinct.mkString("\n")
			log.warn(s"Error(s) calculating RDF diff (got $nUpdates updates) for $tip metadata:\n$errors")
		for updates <- diffV do
			envriServer.applyAll(updates)().fold(
				err => log.error(s"Problem applying $tip station-metadata diff", err),
				_ => log.info(s"Calculated and applied $tip station-metadata diff (${updates.size} RDF changes)")
			)

end StateDiffApplier

