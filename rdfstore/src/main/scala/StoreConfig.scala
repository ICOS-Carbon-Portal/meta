package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.services.citation.{
	StoreCpmetaView,
	CitationClientConfig, CitationRuntimeConfig, CitationStoreConfig, CitationStoreConfigJsonProtocol
}
import spray.json.*

// Each application owns the types for the configuration it parses. Defaults for fields also read
// by meta live in rdf-common's reference.conf under the same `cpmeta` paths; rdfStore uses narrow
// views of that shared configuration contract.
case class DbServer(host: String, port: Int)
case class DbCredentials(db: String, user: String, password: String)
case class RdflogConfig(server: DbServer, credentials: DbCredentials)

case class LmdbConfig(tripleDbSize: Long, valueDbSize: Long, valueCacheSize: Int)

case class RdfStorageConfig(
	lmdb: Option[LmdbConfig],
	path: String,
	recreateAtStartup: Boolean,
	indices: String,
	disableCpIndex: Boolean
)

/** A static, classpath-shipped OWL schema graph that rdfStore ingests itself at
  * startup, since (unlike instance data) it is not recoverable from the rdf log. */
case class SchemaOntologyConfig(writeContext: java.net.URI, owlResource: String)

case class RdfStoreConfig(
	httpBindInterface: String,
	port: Int,
	rdfStorage: RdfStorageConfig,
	schemaOntologies: Seq[SchemaOntologyConfig]
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

	given RootJsonFormat[LmdbConfig] = jsonFormat3(LmdbConfig.apply)
	given RootJsonFormat[RdfStorageConfig] = jsonFormat5(RdfStorageConfig.apply)
	given RootJsonFormat[SchemaOntologyConfig] = jsonFormat2(SchemaOntologyConfig.apply)
	given RootJsonFormat[RdfStoreConfig] = jsonFormat4(RdfStoreConfig.apply)
	given RootJsonFormat[SparqlServerConfig] = jsonFormat7(SparqlServerConfig.apply)

	lazy val default: RdfStoreConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore").parseAs[RdfStoreConfig]

	private lazy val cpmeta = AppConfig.rootConfWithWorkingDirOverrides
		.getValue("cpmeta").parseAs[StoreCpmetaView]

	/** What `CitationProvider` needs, parsed from the same `cpmeta` shape as meta. */
	lazy val citationStoreConfig: CitationStoreConfig =
		val runtime = AppConfig.rootConfWithWorkingDirOverrides
			.getValue("rdfStore.citations").parseAs[CitationRuntimeConfig]
		CitationStoreConfig(
			core = cpmeta.core,
			citations = CitationClientConfig(runtime.style, runtime.eagerWarmUp, runtime.timeoutSec, cpmeta.citations.doi),
			instanceServers = cpmeta.instanceServers,
			dataUploadService = cpmeta.dataUploadService
		)

	/** The same `cpmeta.rdfLog` connection configuration used by meta's log writers. */
	lazy val rdfLogConfig: RdflogConfig = cpmeta.rdfLog

	/** `rdfStore.sparql`, used by `Rdf4jSparqlServer`/`Route`/`QuotaManager` for query throttling. */
	lazy val sparqlConfig: SparqlServerConfig =
		AppConfig.rootConfWithWorkingDirOverrides.getValue("rdfStore.sparql").parseAs[SparqlServerConfig]

end RdfStoreConfigLoader
