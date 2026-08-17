package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.api.HandleNetClientConfig
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.{CitationConfig, CitationConfigJsonProtocol, SharedConfigJsonProtocol}
import spray.json.*

/**
 * The whole configuration `CitationProvider` needs, assembled from two HOCON roots by
 * `RdfStoreConfigLoader.citationStoreConfig`:
 *
 *   - `core`, `citations` and `handle` from the shared `cpmeta` section (rdf-common's
 *     reference.conf), which `meta` reads too;
 *   - `citationGraphs` from rdfStore's own `rdfStore.citationGraphs` (task 25, stage 2), which
 *     replaced the `cpmeta.instanceServers`-shaped read-side copy rdfStore used to carry.
 *
 * Kept next to `CitationProvider` - its only consumer - rather than in the general
 * `StoreConfig.scala`; `cpmeta.sparql`, needed elsewhere in rdfStore for query throttling and
 * unrelated to citations, is loaded as its own `SparqlServerConfig` (`RdfStoreConfigLoader.
 * sparqlConfig`) instead of being a field on this type. Meta's own, much larger `CpmetaConfig`
 * lives in the `meta` module, which rdfStore does not depend on.
 *
 * Note the name: this is *not* `CitationConfig` (rdf-common's `style`/`eagerWarmUp`/`doi` type,
 * held here in the `citations` field) - it is the whole config shape `CitationProvider` needs,
 * of which `CitationConfig` is only one field.
 */
case class CitationStoreConfig(
	core: MetaCoreConfig,
	citations: CitationConfig,
	handle: HandleNetClientConfig,
	citationGraphs: CitationGraphsConfig
)

/**
 * The `cpmeta` half of `CitationStoreConfig`: parsed straight off the shared section, then flattened
 * into `CitationStoreConfig` by the loader. `dataUploadService` is a one-field view on purpose -
 * `metaServers`/`collectionServers`/`documentServers` are meta-only and live in `meta`'s own
 * reference.conf; only `handle` (for `PidFactory`) is still shared.
 */
case class StoreUploadTargetsConfig(handle: HandleNetClientConfig)

case class CitationCpmetaView(
	core: MetaCoreConfig,
	citations: CitationConfig,
	dataUploadService: StoreUploadTargetsConfig
)

object CitationStoreConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given
	import SharedConfigJsonProtocol.given RootJsonFormat[HandleNetClientConfig]
	import CitationConfigJsonProtocol.given RootJsonFormat[CitationConfig]

	given RootJsonFormat[StoreUploadTargetsConfig] = jsonFormat1(StoreUploadTargetsConfig.apply)
	given RootJsonFormat[CitationCpmetaView] = jsonFormat3(CitationCpmetaView.apply)

end CitationStoreConfigJsonProtocol
