package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.services.citation.{CitationStoreConfig, CitationStoreConfigJsonProtocol}
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

object RdfStoreConfigLoader extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import SharedConfigJsonProtocol.given RootJsonFormat[RdflogConfig]
	import CitationStoreConfigJsonProtocol.given RootJsonFormat[CitationStoreConfig]

	given RootJsonFormat[LmdbConfig] = jsonFormat3(LmdbConfig.apply)
	given RootJsonFormat[RdfStorageConfig] = jsonFormat6(RdfStorageConfig.apply)
	given RootJsonFormat[RdfStoreConfig] = jsonFormat6(RdfStoreConfig.apply)
	given RootJsonFormat[SparqlServerConfig] = jsonFormat8(SparqlServerConfig.apply)

	lazy val default: RdfStoreConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore").parseAs[RdfStoreConfig]

	/** The subset of `cpmeta` that `CitationProvider` needs - see `CitationStoreConfig`'s scaladoc. */
	lazy val citationStoreConfig: CitationStoreConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("cpmeta").parseAs[CitationStoreConfig]

	/** `cpmeta.sparql`, used by `Rdf4jSparqlServer`/`Route`/`QuotaManager` for query throttling;
	 *  unrelated to `citationStoreConfig` above even though both live under `cpmeta`. */
	lazy val sparqlConfig: SparqlServerConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("cpmeta.sparql").parseAs[SparqlServerConfig]

end RdfStoreConfigLoader
