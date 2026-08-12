package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.api.HandleNetClientConfig
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import spray.json.*

import java.net.URI

case class LmdbConfig(tripleDbSize: Long, valueDbSize: Long, valueCacheSize: Int)

case class RdfStorageConfig(
	lmdb: Option[LmdbConfig],
	path: String,
	recreateAtStartup: Boolean,
	indices: String,
	disableCpIndex: Boolean,
	recreateCpIndexAtStartup: Boolean
)

case class RdfStoreConfig(
	httpBindInterface: String,
	port: Int,
	rdfStorage: RdfStorageConfig,
	rdfLog: RdflogConfig,
	rdfLogs: Map[String, URI],
	rdfLogRestoreFromId: Map[String, Int]
)

case class SparqlServerConfig(
	maxQueryRuntimeSec: Int,
	quotaPerMinute: Int,//in seconds
	quotaPerHour: Int,  //in seconds
	maxParallelQueries: Int,
	maxQueryQueue: Int,
	banLength: Int, //in minutes
	maxCacheableQuerySize: Int, //in bytes
	adminUsers: Seq[String]
)

/**
 * rdfStore's own view of `cpmeta.instanceServers.specific.*`: just the write/read contexts
 * needed to build RdfLenses. Meta-only fields (logName, skipLogIngestionAtStart,
 * logIngestionFromId, ingestion) live on meta's own InstanceServerConfig and are never parsed
 * here; spray-json's generated readers ignore JSON fields that aren't part of the target case
 * class, so the same `cpmeta.instanceServers.specific.*` HOCON parses fine into both views.
 */
case class StoreInstanceServerConfig(writeContext: URI, readContexts: Option[Seq[URI]])

/**
 * rdfStore only needs the id of the "cpMeta" instance server that metaFlow points at. The
 * upload-target fields (atcUpload, munichUpload, ...) and the `_type` discriminant that pick
 * between IcosMetaFlowConfig/CitiesMetaFlowConfig are meta-only and are ignored here.
 */
case class MetaFlowRef(cpMetaInstanceServerId: String)

case class StoreInstanceServersConfig(
	specific: Map[String, StoreInstanceServerConfig],
	forDataObjects: Map[Envri, DataObjectInstServersConfig],
	metaFlow: OptionalOneOrSeq[MetaFlowRef]
)

case class StoreUploadTargetsConfig(
	metaServers: Map[Envri, String],
	collectionServers: Map[Envri, String],
	documentServers: Map[Envri, String],
	handle: HandleNetClientConfig
)

/**
 * rdfStore's own view of the `cpmeta` config section: only the subset that CitationProvider,
 * Rdf4jSparqlServer and QuotaManager read (docs/rdf-common-split/15-split-config.md). Meta's own,
 * much larger `CpmetaConfig` lives in the `meta` module, which rdfStore does not depend on.
 */
case class StoreMetaConfig(
	core: MetaCoreConfig,
	citations: CitationConfig,
	instanceServers: StoreInstanceServersConfig,
	dataUploadService: StoreUploadTargetsConfig,
	sparql: SparqlServerConfig
)

object RdfStoreConfigLoader extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given
	import SharedConfigJsonProtocol.given RootJsonFormat[RdflogConfig]
	import SharedConfigJsonProtocol.given RootJsonFormat[DataObjectInstServersConfig]
	import SharedConfigJsonProtocol.given RootJsonFormat[HandleNetClientConfig]
	import CitationConfigJsonProtocol.given RootJsonFormat[CitationConfig]

	given RootJsonFormat[LmdbConfig] = jsonFormat3(LmdbConfig.apply)
	given RootJsonFormat[RdfStorageConfig] = jsonFormat6(RdfStorageConfig.apply)
	given RootJsonFormat[RdfStoreConfig] = jsonFormat6(RdfStoreConfig.apply)
	given RootJsonFormat[SparqlServerConfig] = jsonFormat8(SparqlServerConfig.apply)
	given RootJsonFormat[StoreInstanceServerConfig] = jsonFormat2(StoreInstanceServerConfig.apply)
	given RootJsonFormat[MetaFlowRef] = jsonFormat1(MetaFlowRef.apply)
	given RootJsonFormat[StoreInstanceServersConfig] = jsonFormat3(StoreInstanceServersConfig.apply)
	given RootJsonFormat[StoreUploadTargetsConfig] = jsonFormat4(StoreUploadTargetsConfig.apply)
	given RootJsonFormat[StoreMetaConfig] = jsonFormat5(StoreMetaConfig.apply)

	lazy val default: RdfStoreConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore").parseAs[RdfStoreConfig]

	/** rdfStore's narrow view of the `cpmeta` section - see `StoreMetaConfig`'s scaladoc. */
	lazy val metaView: StoreMetaConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("cpmeta").parseAs[StoreMetaConfig]

end RdfStoreConfigLoader
