package se.lu.nateko.cp.meta.rdfstore

import akka.actor.ActorSystem
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.api.{PidFactory, RdfLens, RdfLenses}
import se.lu.nateko.cp.meta.services.citation.CitationClient.{CitationCache, DoiCache}
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationClientImpl, CitationProvider}
import se.lu.nateko.cp.meta.services.external.{ExternalObjFetcher, ExternalProviders}

/** Makes the RDF store's citation provider from the service configuration */
object CitationProviderConfig:

	def citationProvider(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		citationProvider(
			new SailRepository(sail),
			dois => CitationClientImpl(dois, conf.citations, citCache, doiCache),
			conf
		)

	def citationProvider(
		repo: Repository, citClientFactory: List[Doi] => CitationClient, conf: CpmetaConfig
	)(using ActorSystem): CitationProvider =
		new CitationProvider(repo, citClientFactory, conf.core, getLenses(conf), pidFactory(conf), externalObjs(conf))

	private def externalObjs(conf: CpmetaConfig)(using ActorSystem): ExternalObjFetcher =
		new ExternalObjFetcher(ExternalProviders(conf.dataUploadService.externalProviders))

	private def pidFactory(conf: CpmetaConfig): PidFactory =
		val handle = conf.dataUploadService.handle
		new PidFactory(handle.baseUrl, handle.prefix)

	private[rdfstore] def getLenses(conf: CpmetaConfig): RdfLenses =
		val servConf = conf.instanceServers
		val uploadConf = conf.dataUploadService

		def configuredLenses[L](
			serverIds: Map[Envri, String],
			factory: (java.net.URI, Seq[java.net.URI]) => L
		): Map[Envri, L] = serverIds.flatMap: (envri, serverId) =>
			servConf.specific.get(serverId).map: serverConf =>
				envri -> factory(serverConf.writeContext, serverConf.readContexts.getOrElse(Seq(serverConf.writeContext)))

		val perFormat = servConf.forDataObjects.map: (envri, config) =>
			val lenses = config.definitions.map[(java.net.URI, RdfLens.DobjLens)]: definition =>
				val writeContext = new java.net.URI(config.uriPrefix.toString + definition.label + "/")
				definition.format -> RdfLens.dobjLens(writeContext, writeContext +: config.commonReadContexts)
			envri -> lenses.toMap

		RdfLenses(
			metaInstances = Map.empty,
			cpMetaInstances = Map.empty,
			collections = configuredLenses(uploadConf.collectionServers, RdfLens.collLens),
			documents = configuredLenses(uploadConf.documentServers, RdfLens.docLens),
			dobjPerFormat = perFormat
		)
