package se.lu.nateko.cp.meta.services.citation

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.api.{PidFactory, RdfLens, RdfLenses}

object CitationProviderConfig:
	def pidFactory(conf: CpmetaConfig): PidFactory =
		val handle = conf.dataUploadService.handle
		new PidFactory(handle.baseUrl, handle.prefix)

	def getLenses(conf: CpmetaConfig): RdfLenses =
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
