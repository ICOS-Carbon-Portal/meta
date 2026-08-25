package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.{DbCredentials, DbServer, DoiConfig, DoiConfigJsonProtocol, RdflogConfig}
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

case class StoreInstanceServerConfig(
	writeContext: URI,
	readContexts: Option[Seq[URI]],
	logName: Option[String],
	logIngestionFromId: Option[Int]
)

case class StoreDataObjectServerDefinition(
	label: String,
	format: URI,
	replayLogFrom: Option[Int] = None
)

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

case class StoreCpmetaView(
	core: MetaCoreConfig,
	citations: CitationConfig,
	instanceServers: StoreInstanceServersConfig,
	dataUploadService: StoreUploadServiceConfig,
	rdfLog: RdflogConfig
)

object CitationStoreConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given
	import DoiConfigJsonProtocol.given RootJsonFormat[DoiConfig]

	given RootJsonFormat[DbServer] = jsonFormat2(DbServer.apply)
	given RootJsonFormat[DbCredentials] = jsonFormat3(DbCredentials.apply)
	given RootJsonFormat[RdflogConfig] = jsonFormat2(RdflogConfig.apply)
	given RootJsonFormat[CitationConfig] = jsonFormat1(CitationConfig.apply)
	given RootJsonFormat[HandleConfig] = jsonFormat2(HandleConfig.apply)
	given RootJsonFormat[StoreInstanceServerConfig] = jsonFormat4(StoreInstanceServerConfig.apply)
	given RootJsonFormat[StoreDataObjectServerDefinition] = jsonFormat3(StoreDataObjectServerDefinition.apply)
	given RootJsonFormat[StoreDataObjectServersConfig] = jsonFormat3(StoreDataObjectServersConfig.apply)
	given RootJsonFormat[StoreInstanceServersConfig] = jsonFormat2(StoreInstanceServersConfig.apply)
	given RootJsonFormat[StoreUploadServiceConfig] = jsonFormat3(StoreUploadServiceConfig.apply)
	given RootJsonFormat[StoreCpmetaView] = jsonFormat5(StoreCpmetaView.apply)
	given RootJsonFormat[CitationRuntimeConfig] = jsonFormat3(CitationRuntimeConfig.apply)

end CitationStoreConfigJsonProtocol
