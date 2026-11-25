package se.lu.nateko.cp.meta.rdfstore

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.{InstanceServersConfig, UploadServiceConfig}
import se.lu.nateko.cp.meta.core.data.flattenToSeq
import se.lu.nateko.cp.meta.api.{RdfLens, RdfLenses}

/** Builds the RDF lenses from the instance-server and upload configuration. */
object CitationProviderConfig {

	def getLenses(servConf: InstanceServersConfig, uploadConf: UploadServiceConfig): RdfLenses = {

		def configuredLenses[L](
			serverIds: Map[Envri, String],
			factory: (java.net.URI, Seq[java.net.URI]) => L
		): Map[Envri, L] = serverIds.flatMap { (envri, serverId) =>
			servConf.specific.get(serverId).map { serverConf =>
				envri -> factory(serverConf.writeContext, serverConf.readContexts.getOrElse(Seq(serverConf.writeContext)))
			}
		}

		val perFormat = servConf.forDataObjects.map { (envri, config) =>
			val lenses = config.definitions.map[(java.net.URI, RdfLens.DobjLens)] { definition =>
				val writeContext = new java.net.URI(config.uriPrefix.toString + definition.label + "/")
				definition.format -> RdfLens.dobjLens(writeContext, writeContext +: config.commonReadContexts)
			}
			envri -> lenses.toMap
		}

		val cpOwn = servConf.metaFlow.flattenToSeq
			.flatMap { flowConf =>
				val serverId = flowConf.cpMetaInstanceServerId
				servConf.specific.get(serverId).map[(String, RdfLens.CpLens)] { serverConf =>
					serverId -> RdfLens.cpLens(serverConf.writeContext, serverConf.readContexts.getOrElse(Seq(serverConf.writeContext)))
				}
			}
			.toMap

		RdfLenses(
			metaInstances = configuredLenses(uploadConf.metaServers, RdfLens.metaLens),
			cpMetaInstances = cpOwn,
			collections = configuredLenses(uploadConf.collectionServers, RdfLens.collLens),
			documents = configuredLenses(uploadConf.documentServers, RdfLens.docLens),
			dobjPerFormat = perFormat
		)
	}
}
