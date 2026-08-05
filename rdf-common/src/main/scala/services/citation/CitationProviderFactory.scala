package se.lu.nateko.cp.meta.services.citation

import akka.actor.ActorSystem
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens, RdfLenses}
import se.lu.nateko.cp.meta.core.data.flattenToSeq
import se.lu.nateko.cp.meta.{CpmetaConfig, DataObjectInstServerDefinition, DataObjectInstServersConfig, InstanceServersConfig, UploadServiceConfig}

import CitationClient.{CitationCache, DoiCache}

/**
 * `CitationProvider` (in rdf-common) cannot depend on `CpmetaConfig` (still in rdfStore until
 * task 14 moves it to rdf-common), so this factory does the CpmetaConfig -> RdfLenses/PidFactory
 * translation that `CitationProvider` itself used to do inline, and hands `CitationProvider`
 * only the already-resolved pieces it needs. See task 11's deviation note in
 * docs/rdf-common-split/11-move-citation-stack.md.
 */
object CitationProviderFactory:

	def apply(
		sail: Sail, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		CitationProvider(
			sail, citCache, doiCache, conf.core, conf.citations, getLenses(conf.instanceServers, conf.dataUploadService), pidFactory(conf)
		)

	def apply(
		repo: Repository, citCache: CitationCache, doiCache: DoiCache, conf: CpmetaConfig
	)(using ActorSystem, Materializer): CitationProvider =
		CitationProvider(
			repo, citCache, doiCache, conf.core, conf.citations, getLenses(conf.instanceServers, conf.dataUploadService), pidFactory(conf)
		)

	def pidFactory(conf: CpmetaConfig): HandleNetClient.PidFactory =
		new HandleNetClient.PidFactory(conf.dataUploadService.handle)

	def getInstServerContext(conf: DataObjectInstServersConfig, servDef: DataObjectInstServerDefinition) =
		new java.net.URI(conf.uriPrefix.toString + servDef.label + "/")

	def getLenses(servConf: InstanceServersConfig, uplConf: UploadServiceConfig): RdfLenses =
		def confsToLenses[L](confs: Map[Envri, String], factory: (java.net.URI, Seq[java.net.URI]) => L): Map[Envri, L] = confs.flatMap:
			(envri, instServId) => servConf.specific.get(instServId).map: conf =>
				envri -> factory(conf.writeContext, conf.readContexts.getOrElse(Seq(conf.writeContext)))

		val perFormat = servConf.forDataObjects.map: (envri, config) =>
			val lenses = config.definitions.map[(java.net.URI, RdfLens.DobjLens)]: definition =>
				val writeContext = getInstServerContext(config, definition)
				definition.format -> RdfLens.dobjLens(writeContext, writeContext +: config.commonReadContexts)
			envri -> lenses.toMap

		val cpOwn = servConf.metaFlow.flattenToSeq.flatMap: flow =>
			servConf.specific.get(flow.cpMetaInstanceServerId).map[(String, RdfLens.CpLens)]: config =>
				flow.cpMetaInstanceServerId -> RdfLens.cpLens(
					config.writeContext,
					config.readContexts.getOrElse(Seq(config.writeContext))
				)
		.toMap

		RdfLenses(
			metaInstances = confsToLenses(uplConf.metaServers, RdfLens.metaLens),
			cpMetaInstances = cpOwn,
			collections = confsToLenses(uplConf.collectionServers, RdfLens.collLens),
			documents = confsToLenses(uplConf.documentServers, RdfLens.docLens),
			dobjPerFormat = perFormat
		)

end CitationProviderFactory
