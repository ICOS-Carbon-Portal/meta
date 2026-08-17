package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.HandleNetClientConfig
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.{CitationConfig, CitationConfigJsonProtocol, DataObjectInstServersConfig, SharedConfigJsonProtocol}
import spray.json.*

import java.net.URI

/**
 * rdfStore's own view of `cpmeta.instanceServers.specific.*`: just the write/read contexts
 * needed to build RdfLenses. Meta owns a separate, complete configuration containing logName,
 * skipLogIngestionAtStart, logIngestionFromId and ingestion; none of those fields are present or
 * parsed here.
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
 * rdfStore's own view of the `cpmeta` config section: exactly the four fields `CitationProvider`
 * needs to build `RdfLenses`/`PidFactory` (docs/rdf-common-split/15-split-config.md). Kept next to
 * `CitationProvider` - its only consumer - rather than in the general `StoreConfig.scala`;
 * `cpmeta.sparql`, needed elsewhere in rdfStore for query throttling and unrelated to citations,
 * is loaded as its own `SparqlServerConfig` (`RdfStoreConfigLoader.sparqlConfig`) instead of being
 * a field on this type. Meta's own, much larger `CpmetaConfig` lives in the `meta` module, which
 * rdfStore does not depend on.
 *
 * Note the name: this is *not* `CitationConfig` (rdf-common's `style`/`eagerWarmUp`/`doi` type,
 * held here in the `citations` field) - it is the whole config shape `CitationProvider` needs,
 * of which `CitationConfig` is only one field.
 */
case class CitationStoreConfig(
	core: MetaCoreConfig,
	citations: CitationConfig,
	instanceServers: StoreInstanceServersConfig,
	dataUploadService: StoreUploadTargetsConfig
)

object CitationStoreConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given
	import SharedConfigJsonProtocol.given RootJsonFormat[DataObjectInstServersConfig]
	import SharedConfigJsonProtocol.given RootJsonFormat[HandleNetClientConfig]
	import CitationConfigJsonProtocol.given RootJsonFormat[CitationConfig]

	given RootJsonFormat[StoreInstanceServerConfig] = jsonFormat2(StoreInstanceServerConfig.apply)
	given RootJsonFormat[MetaFlowRef] = jsonFormat1(MetaFlowRef.apply)
	given RootJsonFormat[StoreInstanceServersConfig] = jsonFormat3(StoreInstanceServersConfig.apply)
	given RootJsonFormat[StoreUploadTargetsConfig] = jsonFormat4(StoreUploadTargetsConfig.apply)
	given RootJsonFormat[CitationStoreConfig] = jsonFormat4(CitationStoreConfig.apply)

end CitationStoreConfigJsonProtocol
