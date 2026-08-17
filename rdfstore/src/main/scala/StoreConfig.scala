package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.services.citation.{
	CitationCpmetaView, CitationGraphsConfig, CitationGraphsConfigJsonProtocol,
	CitationClientConfig, CitationRuntimeConfig, CitationStoreConfig, CitationStoreConfigJsonProtocol
}
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
	maxCacheableQuerySize: Int //in bytes
)

object RdfStoreConfigLoader extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import SharedConfigJsonProtocol.given RootJsonFormat[RdflogConfig]
	import CitationStoreConfigJsonProtocol.given
	import CitationGraphsConfigJsonProtocol.given

	given RootJsonFormat[LmdbConfig] = jsonFormat3(LmdbConfig.apply)
	given RootJsonFormat[RdfStorageConfig] = jsonFormat6(RdfStorageConfig.apply)
	given RootJsonFormat[RdfStoreConfig] = jsonFormat6(RdfStoreConfig.apply)
	given RootJsonFormat[SparqlServerConfig] = jsonFormat7(SparqlServerConfig.apply)

	lazy val default: RdfStoreConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore").parseAs[RdfStoreConfig]

	/** Store-owned graph scopes for citation reads (task 25) - see `CitationGraphsConfig`. */
	lazy val citationGraphs: CitationGraphsConfig =
		AppConfig.rootConfWithWorkingDirOverrides
			.getValue("rdfStore.citationGraphs").parseAs[CitationGraphsConfig].validated

	/**
	 * What `CitationProvider` needs, assembled from shared core/DOI settings and rdfStore-owned
	 * citation policy and graph scopes - see `CitationStoreConfig`'s scaladoc.
	 */
	lazy val citationStoreConfig: CitationStoreConfig =
		val cpmeta = AppConfig.rootConfWithWorkingDirOverrides
			.getValue("cpmeta").parseAs[CitationCpmetaView]
		val runtime = AppConfig.rootConfWithWorkingDirOverrides
			.getValue("rdfStore.citations").parseAs[CitationRuntimeConfig]
		CitationStoreConfig(
			core = cpmeta.core,
			citations = CitationClientConfig(runtime.style, runtime.eagerWarmUp, runtime.timeoutSec, cpmeta.citations.doi),
			handle = cpmeta.dataUploadService.handle,
			citationGraphs = citationGraphs
		)

	/** `rdfStore.sparql`, used by `Rdf4jSparqlServer`/`Route`/`QuotaManager` for query throttling. */
	lazy val sparqlConfig: SparqlServerConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore.sparql").parseAs[SparqlServerConfig]

end RdfStoreConfigLoader
