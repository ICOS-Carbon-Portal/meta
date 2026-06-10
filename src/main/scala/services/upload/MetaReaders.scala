package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.{CpmetaConfig, MetaDb}
import se.lu.nateko.cp.meta.api.HandleNetClient
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.citation.MaterializedCitationInfoProvider
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}

/**
 * The metadata-reading hub of the meta service: it owns the triplestore
 * repository handle, the vocabularies, the RDF lenses and a [[StaticObjectReader]].
 *
 * Citation values are sourced from the triplestore via
 * [[MaterializedCitationInfoProvider]] — the live computation now lives in the
 * standalone citations service. This used to be bundled into `CitationProvider`,
 * which has moved (together with the live citation computation) into that service.
 */
class MetaReaders(val repo: Repository, conf: CpmetaConfig)(using system: ActorSystem):
	private given envriConfs: EnvriConfigs = conf.core.envriConfigs

	val server = new Rdf4jInstanceServer(repo)
	val metaVocab = new CpmetaVocab(repo.getValueFactory)
	val vocab = new CpVocab(repo.getValueFactory)
	val lenses = MetaDb.getLenses(conf.instanceServers, conf.dataUploadService)

	val metaReader =
		val pidFactory = new HandleNetClient.PidFactory(conf.dataUploadService.handle)
		val citer = new MaterializedCitationInfoProvider(vocab, metaVocab)
		StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)

end MetaReaders
