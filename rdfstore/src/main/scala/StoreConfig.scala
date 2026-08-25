package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.services.citation.{
	CitationCpmetaView,
	CitationClientConfig, CitationRuntimeConfig, CitationStoreConfig, CitationStoreConfigJsonProtocol
}
import spray.json.*

import java.net.URI

// Config value types formerly shared via rdf-common's SharedConfig.scala. rdf-common no longer
// defines application config sections: each application owns the types for the sections it
// parses. `meta` has its own structurally-similar copies, matching its own `cpmeta.rdfLog`
// section, just as it has its own copy of the HOCON defaults.
case class DbServer(host: String, port: Int)
case class DbCredentials(db: String, user: String, password: String)
case class RdflogConfig(server: DbServer, credentials: DbCredentials)

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
	import CitationStoreConfigJsonProtocol.given

	given RootJsonFormat[DbServer] = jsonFormat2(DbServer.apply)
	given RootJsonFormat[DbCredentials] = jsonFormat3(DbCredentials.apply)
	given RootJsonFormat[RdflogConfig] = jsonFormat2(RdflogConfig.apply)
	given RootJsonFormat[LmdbConfig] = jsonFormat3(LmdbConfig.apply)
	given RootJsonFormat[RdfStorageConfig] = jsonFormat6(RdfStorageConfig.apply)
	given RootJsonFormat[RdfStoreConfig] = jsonFormat6(RdfStoreConfig.apply)
	given RootJsonFormat[SparqlServerConfig] = jsonFormat7(SparqlServerConfig.apply)

	lazy val default: RdfStoreConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore").parseAs[RdfStoreConfig]

	/** What `CitationProvider` needs, parsed from the same `cpmeta` shape as meta. */
	lazy val citationStoreConfig: CitationStoreConfig =
		val cpmeta = AppConfig.rootConfWithWorkingDirOverrides
			.getValue("cpmeta").parseAs[CitationCpmetaView]
		val runtime = AppConfig.rootConfWithWorkingDirOverrides
			.getValue("rdfStore.citations").parseAs[CitationRuntimeConfig]
		CitationStoreConfig(
			core = cpmeta.core,
			citations = CitationClientConfig(runtime.style, runtime.eagerWarmUp, runtime.timeoutSec, cpmeta.citations.doi),
			instanceServers = cpmeta.instanceServers,
			dataUploadService = cpmeta.dataUploadService
		)

	/** `rdfStore.sparql`, used by `Rdf4jSparqlServer`/`Route`/`QuotaManager` for query throttling. */
	lazy val sparqlConfig: SparqlServerConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore.sparql").parseAs[SparqlServerConfig]

end RdfStoreConfigLoader
