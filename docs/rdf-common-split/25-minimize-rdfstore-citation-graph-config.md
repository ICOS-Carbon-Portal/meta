# 25 — Minimize rdfStore's citation graph configuration

## Status

Completed (Stages 1 and 2). This document records the former runtime dependency on
`cpmeta.instanceServers`, the unused configuration it exposed, and the resulting smaller
store-owned graph-reading configuration.

The implemented change is a local configuration refactor. Sending this configuration from `meta`
over HTTP is considered below, but is not recommended because it creates a distributed startup
protocol for static deployment data. Moving all derived-metadata computation back to `meta` is a
separate, larger design option and should not be mixed into this change.

### Completion

`rdfStore.citationGraphs` now holds direct ICOS/SITES collection, document and data-object graph
scopes. `CitationProvider` constructs `RdfLenses` from that model, with empty metadata-instance
and portal-metadata maps; its shared `cpmeta` view contains only `core`, `citations` and Handle
configuration. The loader validates that every configured ENVRI has all three graph categories.
`CitationDerivationTest` covers the configured lenses and data-object/document/collection
derivation, while `RouteTest` asserts that rdfStore no longer carries `cpmeta.instanceServers`.

The remaining sections preserve the audit and rationale that led to the change.

## Context

`meta` and rdfStore now own independent HOCON views:

- `meta` has the complete `cpmeta.instanceServers` configuration, including ingestion, RDF-log,
  editor, ontology and metaflow settings;
- rdfStore has a smaller copy containing graph contexts and format mappings used to construct
  `RdfLenses` for `CitationProvider`;
- rdf-common contains no `cpmeta.instanceServers` values.

This removed accidental configuration ownership from rdf-common, but rdfStore's supposedly
minimal view still mirrors more of `meta`'s instance-server model than its runtime code needs.
In particular, the name `instanceServers` obscures the store's actual concern: choosing the RDF
named graphs from which citation inputs are read.

## When rdfStore reads and uses the configuration

### Startup

`rdfstore.Main` loads `RdfStoreConfigLoader.citationStoreConfig` immediately when the process
starts. Before the HTTP listener is bound, and before RDF-log restoration, startup performs these
steps:

1. load the citation, core, upload-target and instance-server configuration;
2. open the persistent Sail;
3. construct `CitationProvider`;
4. convert `StoreInstanceServersConfig` and `StoreUploadTargetsConfig` into `RdfLenses`;
5. construct `StaticObjectReader` with those lenses;
6. wrap the Sail with derived-statement enrichment;
7. restore RDF logs and indexes;
8. bind the HTTP routes.

The HOCON configuration is therefore read once. It is not consulted dynamically after startup.
The resulting immutable lenses are retained by `StaticObjectReader`.

RDF-log restoration does **not** use this configuration. `RdfLogManager` independently reads
`rdfStore.rdfLogs`, the mapping from RDF-log names to named graph IRIs.

### Runtime

The graph lenses are used when `StaticObjectReader` reconstructs a data object, document object or
collection. This occurs through `CitationProvider` when rdfStore derives:

- bibliography/reference JSON (`cpmeta:hasBiblioInfo`);
- citation strings (`cpmeta:hasCitationString`);
- licences (`dcterms:license`).

There are two entry points to the same `DerivedMetadataService`:

1. SPARQL statement enrichment. A query for one of the virtual predicates synchronously resolves
   the subject through `CitationProvider` during RDF4J query evaluation.
2. `POST /internal/derived/v1/resolve`. `meta` calls this batch endpoint to enrich landing pages,
   DTOs, DOI metadata and other representations with the same canonical derived values.

`StaticObjectReader` is also passed to `GeoIndexProvider`, but the geo-index code only calls reader
methods against the ambient global connection. It does not call `RdfLenses.documentLens`,
`collectionLens`, `dataObjectLens`, `metaInstanceLens` or `cpLens`. The current instance-server
configuration is consequently not required for the observed geo-index access path, even though
the same reader object is used.

## Audited lens usage

`CitationProvider.getLenses` currently builds five lens categories:

| Lens category | Constructed from | Observed rdfStore consumer | Keep? |
| --- | --- | --- | --- |
| metadata instances | `dataUploadService.metaServers` plus `specific.*` | none | no |
| portal metadata | `instanceServers.metaFlow.cpMetaInstanceServerId` plus `specific.*` | none | no |
| collections | `dataUploadService.collectionServers` plus `specific.*` | `StaticObjectReader` parent/collection reads | yes |
| documents | `dataUploadService.documentServers` plus `specific.*` | document objects, specifications and related metadata | yes |
| data objects by format | `instanceServers.forDataObjects` | selecting the data-object graph after reading its format | yes |

Repository-wide call-site inspection finds no rdfStore call to `RdfLenses.metaInstanceLens` or
`RdfLenses.cpLens`. Those categories are useful to `meta`, but their construction inside rdfStore
is residue from sharing the broader `RdfLenses` abstraction.

## Configuration that can be removed now

The following values can be removed from rdfStore without changing the observed citation read
path:

- `cpmeta.instanceServers.metaFlow`;
- `MetaFlowRef` and its JSON format;
- `specific.instances`;
- `dataUploadService.metaServers` in `StoreUploadTargetsConfig`;
- `specific.icos`, referenced only by `metaServers.ICOS`;
- `specific.sitesmeta`, referenced only by `metaServers.SITES`;
- construction of `metaInstances` and `cpMetaInstances` in `CitationProvider.getLenses`.

After that reduction, rdfStore needs only:

- the ICOS and SITES collection graph definitions;
- the ICOS and SITES document graph definitions;
- data-object graph definitions by ENVRI and object format;
- Handle base URL and prefixes for `PidFactory`;
- ordinary citation and core configuration, which is outside the instance-server concern.

This first reduction may continue to instantiate the existing `RdfLenses` type with empty
`metaInstances` and `cpMetaInstances` maps. That keeps the change small and establishes through
tests that those categories are genuinely unused before changing the model.

## Store-owned model

The next step should stop representing read graphs indirectly as instance-server IDs. rdfStore
does not create or administer `InstanceServer`s from this configuration. It only needs resolved
graph scopes. A direct model makes that ownership explicit:

```scala
case class ReadGraphConfig(
  primaryContext: URI,
  readContexts: Seq[URI]
)

case class DataObjectGraphConfig(
  commonReadContexts: Seq[URI],
  uriPrefix: URI,
  formats: Map[URI, String]
)

case class CitationGraphsConfig(
  collections: Map[Envri, ReadGraphConfig],
  documents: Map[Envri, ReadGraphConfig],
  dataObjects: Map[Envri, DataObjectGraphConfig]
)
```

The precise representation of `formats` can retain `DataObjectInstServerDefinition` initially to
avoid unrelated churn. A later cleanup can replace the label-oriented definition with a direct
format-to-context mapping.

The corresponding HOCON should live in rdfStore's own `reference.conf` and use store terminology:

```hocon
rdfStore.citationGraphs {
  collections {
    ICOS {
      primaryContext = "http://meta.icos-cp.eu/collections/"
      readContexts = [
        "http://meta.icos-cp.eu/collections/",
        "http://meta.icos-cp.eu/resources/cpmeta/",
        "http://meta.icos-cp.eu/ontologies/cpmeta/",
        "http://meta.icos-cp.eu/resources/icos/"
      ]
    }
    SITES { /* SITES collection scope */ }
  }

  documents {
    ICOS { /* ICOS document scope */ }
    SITES { /* SITES document scope */ }
  }

  dataObjects {
    ICOS {
      commonReadContexts = [ /* shared ICOS read contexts */ ]
      uriPrefix = "http://meta.icos-cp.eu/resources/"
      definitions = [ /* format and graph-label pairs */ ]
    }
    SITES { /* SITES data-object mappings */ }
  }
}
```

This deliberately duplicates the graph topology needed by rdfStore. It does not duplicate
journalling, ingestion, metaflow, ontology-server or editor configuration. The duplication is an
explicit deployment contract between independently startable applications rather than a shared
application configuration object.

### Primary versus write context

The current config calls the first graph `writeContext` because it originated in the mutable
`InstanceServer` model. rdfStore's citation reader does not write through these lenses. In the new
model the field should be called `primaryContext`.

`RdfLens.mkLens` still requires a primary context as well as read contexts. Where the old
`readContexts` was absent, configuration loading currently falls back to the old `writeContext`.
The new format should make that resolution explicit: either require a non-empty `readContexts`, or
default it to `Seq(primaryContext)` while parsing. The resolved runtime type should never contain
an optional read-context list.

## Migration plan (completed)

### Stage 1 — prove and remove unused lens categories

1. Add tests that exercise citation/reference/licence derivation for a data object, document and
   collection under both supported ENVRIs.
2. Assert that rdfStore's parsed `specific` map contains only the four collection/document target
   records.
3. Remove `metaServers`, `MetaFlowRef`, `metaFlow`, `instances`, `icos` and `sitesmeta` from the
   rdfStore config and config types.
4. Construct `RdfLenses` with empty metadata-instance and portal-metadata maps.
5. Run the complete rdfStore test suite and the SPARQL regression corpus.

This stage changes no read contexts and should be behavior-preserving.

### Stage 2 — flatten the configuration

1. Introduce `ReadGraphConfig` and `CitationGraphsConfig` next to `CitationProvider`.
2. Move the remaining values from `cpmeta.instanceServers` and the collection/document target
   fields under `rdfStore.citationGraphs`.
3. Change `CitationProvider.getLenses` to consume direct ENVRI-to-graph maps; remove the
   server-ID lookup and silent `flatMap` omission.
4. Fail configuration loading when an ENVRI has a target without a graph definition.
5. Remove the rdfStore-side `StoreInstanceServerConfig`, `StoreInstanceServersConfig`, and the
   collection/document fields from `StoreUploadTargetsConfig`.
6. Keep Handle configuration separately until citation configuration receives a broader cleanup.

The current `flatMap` silently drops a configured ENVRI when its referenced server ID is missing.
The direct model should make such mismatches unrepresentable or fail eagerly at startup.

### Stage 3 — optionally narrow the shared Scala API

Once only `meta` needs the full `RdfLenses` aggregate, consider giving rdfStore's reader a smaller
interface containing only `documentLens`, `collectionLens`, and `dataObjectLens`. Alternatives are:

- a `CitationRdfLenses` type implemented directly by the new config;
- constructor parameters for the three lens functions;
- splitting `StaticObjectReader` into a graph-agnostic parser and a graph-scope resolver.

Do this only after Stage 1 proves the unused categories. The goal is to express the narrower
dependency, not merely move types between modules.

## Should meta send this configuration over an endpoint?

### Startup registration endpoint

`meta` could theoretically post a graph-topology DTO to rdfStore. This would remove duplicated
HOCON, but it would add the following protocol:

1. rdfStore starts in an unready bootstrap state;
2. it binds a restricted registration endpoint before constructing `CitationProvider`;
3. `meta` starts, authenticates and posts a versioned topology document;
4. rdfStore validates it, constructs its readers and completes startup;
5. restarts and configuration changes require persistence or re-registration.

This conflicts with the current dependency direction. `meta` normally needs rdfStore's SPARQL
endpoint to construct its RDF-backed services, while rdfStore would now wait for `meta` before
becoming ready. Resolving that cycle requires separate bootstrap and ready states, retry behavior,
authentication, idempotency, version negotiation and operational monitoring.

Those costs are disproportionate for static graph IRIs. A deployment template that generates both
applications' local configuration provides a single source of truth without creating a runtime
dependency.

### rdfStore calling meta during query evaluation

Moving citation derivation to a meta endpoint and having rdfStore call it is more problematic.
SPARQL magic predicates are resolved synchronously inside RDF4J query evaluation. `meta` would
normally reconstruct the requested item by querying rdfStore, producing this call chain:

```text
client SPARQL query
  -> rdfStore statement enrichment
    -> meta derived-metadata endpoint
      -> rdfStore SPARQL query
```

This creates a runtime cycle in the query hot path. It also adds network latency per subject,
requires blocking or redesigning the synchronous RDF4J interface, and makes ordinary rdfStore
queries depend on meta's availability. Batching helps the HTTP endpoint used by `meta`, but does
not naturally help RDF4J's subject-at-a-time statement enrichment.

This direction should not be introduced while virtual derived triples remain store-owned.

## Larger alternatives

### Store graph topology as RDF

Instead of sending configuration from a live `meta` process, graph roles could be represented in a
small control graph that is written by meta and included in RDF-log restoration. rdfStore could
restore the control graph first, read and validate it, then construct `CitationProvider`.

Advantages:

- one durable source of graph topology;
- topology changes follow the same logged data path as other metadata;
- no live meta dependency during rdfStore startup or query evaluation.

Costs:

- startup must be reordered because `CitationProvider` is currently constructed before RDF-log
  restoration;
- the control vocabulary and compatibility rules become a public operational contract;
- a missing or invalid control graph needs a well-defined failure or fallback policy;
- configuration needed to locate and restore the control graph still has to be bootstrapped.

This is viable if graph topology is expected to change dynamically. It is unnecessary complexity
if the topology changes only during deployments.

### Query all graphs dynamically

The reader could avoid configured lenses and query the global repository view. It already performs
some global lookups, such as determining object type and format and following version links.

Removing graph scopes entirely would avoid duplicated topology, but changes semantics:

- duplicate statements in unrelated graphs may become visible;
- ENVRI isolation becomes implicit rather than enforced by configured contexts;
- broad repository scans may be slower;
- malformed or stale graphs can influence derived metadata.

A safer dynamic approach would first discover the named context containing the subject and then
resolve a bounded set of associated contexts. That is a substantial reader redesign and needs
production-sized performance testing.

### Materialize derived metadata

The cleanest ownership boundary is for `meta` to compute citations, references and licences when
metadata changes, then write them as ordinary logged RDF statements. rdfStore would store and query
those triples without `CitationProvider`, `CitationMaker`, DOI caches, object reconstruction or
virtual statement enrichment.

This eliminates the configuration discussed in this document from rdfStore's citation path, but
introduces a derived-data pipeline:

- recompute when source metadata, DOI metadata, citation style or licence rules change;
- invalidate and retry external DOI lookups;
- update collections when member-derived values change;
- migrate existing data and define consistency expectations during recomputation;
- decide whether derived triples belong in the same RDF log or a separate materialized view.

This should be treated as a separate architecture project. It should not block the low-risk local
configuration reduction.

## Compatibility and deployment

Stage 1 can preserve all externally visible configuration paths except the rdfStore-only fields
proved unused. Stage 2 intentionally moves the remaining store paths from
`cpmeta.instanceServers` to `rdfStore.citationGraphs`.

Before Stage 2 is deployed:

1. update development and production overrides;
2. update deployment templates and inventories;
3. compare the fully resolved old and new graph configurations;
4. provide a temporary compatibility loader if rolling deployments can run mixed application
   versions;
5. fail startup with a precise message when neither the new nor compatibility path is complete.

No meta configuration path needs to change. Meta continues to own its complete
`cpmeta.instanceServers` section.

## Verification

The implementation is complete when all of the following hold:

- rdf-common contains no instance-server HOCON values;
- rdfStore's effective config contains no `logName`, ingestion, replay, metaflow, ontology or
  editor settings;
- rdfStore no longer parses `MetaFlowRef` or `dataUploadService.metaServers`;
- every configured ENVRI has document, collection and data-object graph scopes, validated at
  startup;
- data-object, document and collection derived metadata remains byte-for-byte compatible at the
  HTTP boundary;
- the three SPARQL magic predicates remain in parity with `/internal/derived/v1/resolve`;
- DOI cache invalidation still affects both the HTTP and SPARQL representations;
- geo-index initialization and update tests remain green, proving that the removed lens categories
  were not an accidental dependency;
- fresh RDF-log restoration remains independent of citation graph configuration;
- `meta` starts and parses its complete instance-server configuration without rdfStore resources
  on its classpath;
- `sbt rdfCommon/compile rdfStore/compile meta/compile`, `rdfStore/Test/test`, and
  `meta/Test/compile` pass.

For the direct configuration migration, add a test that loads old and new fixtures, resolves them
to normalized `CitationGraphsConfig`, and asserts structural equality. This catches an omitted read
context more reliably than comparing rendered HOCON.

## Recommended decision

Proceed with Stages 1 and 2:

1. delete the unused metadata-instance and portal-metadata lens configuration;
2. replace the remaining instance-server-shaped copy with direct store-owned
   `rdfStore.citationGraphs` configuration;
3. keep both applications independently startable;
4. use deployment generation, rather than an application endpoint, if a single configuration
   source is required.

Do not make rdfStore call meta from SPARQL statement enrichment. Reconsider endpoint delivery only
if graph topology becomes genuinely dynamic and operational requirements justify a versioned
bootstrap protocol. Consider materialized derived triples separately if the desired long-term
boundary is for rdfStore to become a domain-agnostic RDF storage and query service.
