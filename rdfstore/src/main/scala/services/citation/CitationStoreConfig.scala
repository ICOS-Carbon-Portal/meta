package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.{CitationConfig, CitationConfigJsonProtocol, DoiConfig}
import spray.json.*

/**
 * The whole configuration `CitationProvider` needs, assembled from two HOCON roots by
 * `RdfStoreConfigLoader.citationStoreConfig`:
 *
 *   - shared `core` and DOI settings from rdf-common's `cpmeta` section;
 *   - citation rendering policy from rdfStore's own `rdfStore.citations` section;
 *   - `handle` from rdfStore's application-owned, narrow `cpmeta.dataUploadService` defaults;
 *   - `citationGraphs` from rdfStore's own `rdfStore.citationGraphs` (task 25, stage 2), which
 *     replaced the `cpmeta.instanceServers`-shaped read-side copy rdfStore used to carry.
 *
 * Kept next to `CitationProvider` - its only consumer - rather than in the general
 * `StoreConfig.scala`; `rdfStore.sparql`, needed elsewhere in rdfStore for query throttling and
 * unrelated to citations, is loaded as its own `SparqlServerConfig` (`RdfStoreConfigLoader.
 * sparqlConfig`) instead of being a field on this type. Meta's own, much larger `CpmetaConfig`
 * lives in the `meta` module, which rdfStore does not depend on.
 *
 * Kept separate from `CitationClientConfig`, which is the rdfStore-only HTTP/cache policy used
 * by `CitationClient`.
 */
case class CitationStoreConfig(
	core: MetaCoreConfig,
	citations: CitationClientConfig,
	handle: HandleConfig,
	citationGraphs: CitationGraphsConfig
)

case class CitationClientConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int, doi: DoiConfig)
case class CitationRuntimeConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int)

/**
 * The `cpmeta` half of `CitationStoreConfig`: parsed from the effective section assembled from
 * rdf-common and rdfStore defaults, then flattened into `CitationStoreConfig` by the loader.
 * `dataUploadService` is a one-field view on purpose -
 * `metaServers`/`collectionServers`/`documentServers` are meta-only and live in `meta`'s own
 * reference.conf; rdfStore defines only `handle` (for `PidFactory`) in its reference.conf.
 */
case class HandleConfig(prefix: Map[Envri, String], baseUrl: String)

case class StoreUploadTargetsConfig(handle: HandleConfig)

case class CitationCpmetaView(
	core: MetaCoreConfig,
	citations: CitationConfig,
	dataUploadService: StoreUploadTargetsConfig
)

object CitationStoreConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given
	import CitationConfigJsonProtocol.given RootJsonFormat[CitationConfig]

	given RootJsonFormat[HandleConfig] = jsonFormat2(HandleConfig.apply)
	given RootJsonFormat[StoreUploadTargetsConfig] = jsonFormat1(StoreUploadTargetsConfig.apply)
	given RootJsonFormat[CitationCpmetaView] = jsonFormat3(CitationCpmetaView.apply)
	given RootJsonFormat[CitationRuntimeConfig] = jsonFormat3(CitationRuntimeConfig.apply)

end CitationStoreConfigJsonProtocol
