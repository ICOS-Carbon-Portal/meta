package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.{DoiConfig, DoiConfigJsonProtocol}
import spray.json.*

import java.net.URI

/**
 * rdfstore's narrow view of the same `cpmeta` configuration parsed by meta. Spray's product
 * readers ignore fields that are not represented by these types, so rdfstore can use the shared
 * configuration layout without knowing about instance-server logging, ingestion, replay,
 * metaflow, upload transforms, or handle-client credentials.
 */
case class CitationStoreConfig(
	core: MetaCoreConfig,
	citations: CitationClientConfig,
	instanceServers: StoreInstanceServersConfig,
	dataUploadService: StoreUploadServiceConfig
)

/** rdfStore's `cpmeta.citations` section. meta has its own, separately-defined one. */
case class CitationConfig(doi: DoiConfig)

case class CitationClientConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int, doi: DoiConfig)
case class CitationRuntimeConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int)

case class HandleConfig(prefix: Map[Envri, String], baseUrl: String)

case class StoreInstanceServerConfig(writeContext: URI, readContexts: Option[Seq[URI]])

case class StoreDataObjectServerDefinition(label: String, format: URI)

case class StoreDataObjectServersConfig(
	commonReadContexts: Seq[URI],
	uriPrefix: URI,
	definitions: Seq[StoreDataObjectServerDefinition]
)

case class StoreInstanceServersConfig(
	specific: Map[String, StoreInstanceServerConfig],
	forDataObjects: Map[Envri, StoreDataObjectServersConfig]
)

case class StoreUploadServiceConfig(
	collectionServers: Map[Envri, String],
	documentServers: Map[Envri, String],
	handle: HandleConfig
)

case class CitationCpmetaView(
	core: MetaCoreConfig,
	citations: CitationConfig,
	instanceServers: StoreInstanceServersConfig,
	dataUploadService: StoreUploadServiceConfig
)

object CitationStoreConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given
	import DoiConfigJsonProtocol.given RootJsonFormat[DoiConfig]

	given RootJsonFormat[CitationConfig] = jsonFormat1(CitationConfig.apply)
	given RootJsonFormat[HandleConfig] = jsonFormat2(HandleConfig.apply)
	given RootJsonFormat[StoreInstanceServerConfig] = jsonFormat2(StoreInstanceServerConfig.apply)
	given RootJsonFormat[StoreDataObjectServerDefinition] = jsonFormat2(StoreDataObjectServerDefinition.apply)
	given RootJsonFormat[StoreDataObjectServersConfig] = jsonFormat3(StoreDataObjectServersConfig.apply)
	given RootJsonFormat[StoreInstanceServersConfig] = jsonFormat2(StoreInstanceServersConfig.apply)
	given RootJsonFormat[StoreUploadServiceConfig] = jsonFormat3(StoreUploadServiceConfig.apply)
	given RootJsonFormat[CitationCpmetaView] = jsonFormat4(CitationCpmetaView.apply)
	given RootJsonFormat[CitationRuntimeConfig] = jsonFormat3(CitationRuntimeConfig.apply)

end CitationStoreConfigJsonProtocol
