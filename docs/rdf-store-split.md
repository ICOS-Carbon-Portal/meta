# Splitting the RDF store from `meta`

## Status and objective

The target is two independently deployable JVM applications:

1. **`rdfStore`** exclusively owns the RDF4J Sail, its LMDB/NativeStore files, query execution, and the SPARQL query/update protocol, including the public `/sparql` endpoint with its query quotas, timeouts and response cache.
2. **`meta`** owns metadata-domain behavior (upload validation, RDF production, landing pages, labeling, ontology editors, citations, DOI integration, metadata flows, and normal RDF-log writes) and accesses RDF through an RDF4J `Repository` client.

The process split is implemented. `rdfStore` owns the RDF implementation and runtime configuration, and provides `/sparql` (public, quota-throttled and cached), `/internal/sparql` (private SPARQL query/update), and `/health`. `meta` no longer serves `/sparql`; the public URL is a reverse-proxy route to `rdfStore`. `meta` always connects through RDF4J `SPARQLRepository` and has no embedded-store fallback.

The build split is also implemented (`docs/rdf-common-split/`, tasks 01-23): the two applications no longer depend on each other at all. The dependency direction is `meta -> rdfCommon -> metaCore` and `rdfStore -> rdfCommon -> metaCore`; neither `meta` nor `rdfStore` depends on the other, and `rdfCommon` must never depend on either application. `rdfCommon` is the shared library for configuration (`CpmetaConfig`), vocabularies, the `InstanceServer` API, generic RDF4J utilities, domain exceptions, the citation/DOI stack, read-side object fetchers, and the neutral PostgreSQL RDF-log reader/writer. It excludes the RDF storage-implementation dependencies (`rdf4j-sail-lmdb`, `rdf4j-sail-nativerdf`, `lwjgl`, `kryo`). A CI task, `checkModuleBoundaries`, fails the build if either application's compiled classpath contains the other's class directory (`docs/rdf-common-split/22-ci-guard.md`).

## Existing coupling (historical: pre-process-split state)

This section describes `meta` before the process split, when a single JVM owned both the
metadata domain and the embedded RDF store. It predates everything else in this document and
does not describe the current codebase; kept for context on why the boundary was drawn where it
was. Before the split, `MetaDbFactory` performed four distinct jobs:

- opens `LmdbStore` or `NativeStore` and controls its filesystem;
- wraps the Sail with Carbon Portal change notifications and custom indexes;
- initializes named graphs from OWL/resources or PostgreSQL RDF logs;
- constructs all metadata-domain services over the resulting `Repository`.

Most domain code is already on a useful abstraction boundary. `Rdf4jInstanceServer`, `Rdf4jSparqlRunner`, URI serialization, ingestion, and upload services consume RDF4J's `Repository`/`RepositoryConnection`, rather than LMDB classes. Local-only coupling is concentrated in:

- `StorageSail` and `CpNotifyingSail` construction;
- custom-index restore/dump/read-only controls;
- `CitationProvider` construction from a `Sail`;
- startup replay and ingestion ownership;
- the assumption that an RDF transaction and PostgreSQL RDF-log append happen in one process.

## Target component boundary

The *process* boundary (what talks to what over the network at runtime) and the *build* graph
(what depends on what at compile time) are different shapes, now that the build split
(`docs/rdf-common-split/`) is done: at runtime there are exactly two processes, `meta` and
`rdfStore`, talking over HTTP; at build time there are four modules, with `rdfCommon` and
`metaCore` as a shared base underneath both applications and never talking to anything at
runtime themselves (they are libraries, not processes).

Process boundary (runtime):

```text
public clients
     |
     v
+---------------- meta ----------------+
| upload, editors, labeling, landing   |
| metadata flows, DOI/citation         |
| RDF4J SPARQLRepository client        |
+------------------+-------------------+
                   | private network
              query| SPARQL 1.1 |update
                   v
+-------------- rdfStore --------------+
| /sparql (public read: quota, cache)   |
| /internal/sparql (query and update)   |
| quota, timeout, serialization         |
| custom indexes and change listeners  |
| RDF4J SailRepository                  |
| LMDB/NativeStore filesystem owner     |
+---------------------------------------+
                   |
             persistent volume
```

Build graph (compile time - no network edges, just `.dependsOn`):

```text
              metaCore          (no RDF4J; published to Nexus, drives TS/Py codegen)
                  ^
              rdfCommon         (RDF4J-level shared code: config, vocab, InstanceServer API,
                  ^  ^            citation/DOI stack, object fetchers)
      rdfStore ---'  '--- meta
```

`rdfStore` and `meta` both depend on `rdfCommon` and, transitively, `metaCore`; neither depends
on the other, and `rdfCommon` depends on neither application. A CI task
(`checkModuleBoundaries`) fails the build if this ever stops being true.

Only `rdfStore` may mount the RDF storage directory. `meta` must not have access to that volume. The public `/sparql` URL is served by `rdfStore` directly, so a reverse proxy must route `<meta host>/sparql` there and overwrite `X-Forwarded-For` with the trusted client address, which is what the per-client query quotas key on. Public requests without that header are rejected. `meta`'s own reads use `/internal/sparql`, which applies neither quotas nor response caching, so internal metadata reads are never served from a stale cache.

## Protocol and repository choice

### Reads

`meta` uses RDF4J `SPARQLRepository` in quad mode. Existing calls to `prepareTupleQuery`, `prepareGraphQuery`, `getStatements`, and `hasStatement` are translated to the remote SPARQL endpoint while retaining RDF4J model types throughout the domain code.

The query endpoint supports:

- GET `?query=...`;
- POST form field `query`;
- POST query text;
- tuple results as SPARQL JSON/XML, CSV, or TSV;
- boolean (`ASK`) results as SPARQL JSON or XML;
- graph results as RDF/XML or Turtle.

### Writes

RDF4J `SPARQLRepository` sends `RepositoryConnection.add/remove` and prepared updates to the private `/internal/sparql` endpoint. Meta wraps each logged `InstanceServer` in `LoggingInstanceServer`, so it appends the original `RdfUpdate` batch to PostgreSQL in the same pre-commit callback used before the process split. Named contexts remain authoritative: each logical `InstanceServer` has its configured read contexts and exactly one normal write context.

The update endpoint must never be public. Production should use service identity (mTLS or a short-lived workload token) in addition to network policy. The initial implementation binds to loopback and relies on network isolation; authentication is a required hardening item before cross-host deployment.

### Why not a bespoke metadata API

A DTO-level RDF API would duplicate RDF4J query, value, context, and transaction semantics and force broad changes through the metadata code. The RDF4J repository client preserves the existing programming model. Domain APIs remain in `meta`; the storage boundary deals only in SPARQL/RDF.

## Ownership rules

| Concern | Owner |
|---|---|
| LMDB/NativeStore files and lifecycle | `rdfStore` |
| SPARQL parsing, execution quotas, cancellation, result formats | `rdfStore` |
| Public SPARQL endpoint: CORS, response caching, client throttling | `rdfStore` |
| Carbon Portal custom query indexes | `rdfStore` |
| Store backups and compaction | `rdfStore` |
| RDF-log writes and change history | `meta` |
| RDF-log replay during store initialization | `rdfStore` |
| Upload validation and RDF statement production | `meta` |
| Ontology-driven editors and labeling workflow | `meta` |
| Landing-page composition | `meta` |
| Public authentication/authorization | `meta` |
| Citation and DOI metadata resolution (`CitationProvider`/`CitationProviderFactory`) | `rdfCommon` (both applications) |
| Configuration model (`CpmetaConfig` and friends) | `rdfCommon` (both applications) |
| RDF vocabularies (`CpVocab`, `CpmetaVocab`, TC vocab constants) | `rdfCommon` (both applications) |
| `InstanceServer` API and generic RDF4J utilities | `rdfCommon` (both applications) |
| Read-side object fetchers (static object/collection readers) | `rdfCommon` (both applications) |

`rdfStore`'s custom Sail enriches statements with citations during query evaluation
(`magic/StatementsEnricher.scala`, `magic/CpNotifyingSail.scala`), so the citation/DOI stack has
to be reachable from inside `rdfStore` itself, in-process - calling back into `meta` over HTTP
from inside query evaluation would create a runtime cycle in the hot path. This is why it is
`rdfCommon`-owned rather than `meta`-owned, unlike the superficially similar landing-page
composition, which stays in `meta` because nothing in `rdfStore`'s query path needs it.

## Transactions and RDF update logs

The current implementation preserves the original `LoggingInstanceServer` behavior. Meta wraps each logged remote `Rdf4jInstanceServer`; its PostgreSQL append runs in the callback immediately before the remote RDF4J client commits the SPARQL Update. rdfStore consumes those logs only when it initializes or performs a configured replay, so replay never appends to the log again.

Capturing every write at the Sail level—including arbitrary SPARQL Update requests—would broaden the original behavior. This is intentionally deferred as a future improvement rather than included in the split.

The two persistence systems still cannot provide a distributed atomic commit: an RDF4J commit failure after a successful PostgreSQL append can leave a log entry ahead of the store. An idempotent mutation/outbox design remains a possible hardening step.

## Custom index migration

`CpNotifyingSail`, `IndexHandler`, `GeoIndexProvider`, storage selection, query evaluation, and citation-backed statement enrichment now live in and execute inside `rdfStore`. Custom indexes are always built from the store's own triples at startup: there is no index snapshot on disk, and no read-only switching. The module depends on `metaCore` for shared data types and algorithms, never on `meta`.

Migration sequence:

1. Storage, indexes, SPARQL execution, RDF access helpers, vocabularies, and enrichment code are compiled by `rdfStore` (implemented).
2. Indexes are built in `rdfStore` before its HTTP listener is bound (implemented).
3. The embedded Sail/index lifecycle has been deleted from `meta` (implemented).

Queries sent through remote mode are evaluated by the same `CpNotifyingSail` and custom indexes in `rdfStore`. Performance and Carbon Portal-specific query rewrites must still be regression-tested across the new HTTP hop before production cutover.

## Startup and readiness

`rdfStore` startup order:

1. open and initialize the Sail repository;
2. on a fresh store, restore each configured RDF log sequentially;
3. build the custom indexes;
4. expose the routes.

The replay is deliberately sequential. This retains the old protection against NativeStore crashes under unrestrained parallel writes, but limits serialization to initial RDF-log restoration; normal store traffic uses RDF4J's transaction concurrency.

`meta` startup order:

1. create `SPARQLRepository` from query/update URLs;
2. execute a bounded readiness query;
3. construct context-scoped instance servers and domain services;
4. do not restore local indexes or touch RDF storage files;
5. expose public routes only after required reference graphs are present.

Readiness should distinguish `live` (process responds) from `ready` (repository and indexes are usable). Include a store-generation identifier in readiness so operators can verify that all `meta` instances point to the intended store.

## Configuration

`meta` requires the remote repository configuration:

```hocon
	cpmeta.remoteRdfRepository {
	  queryEndpoint = "http://127.0.0.1:9095/internal/sparql"
	  updateEndpoint = "http://127.0.0.1:9095/internal/sparql"
	}
```

The standalone application owns the storage and RDF-log reader implementation. RDF storage uses
`rdfStore.rdfStorage`; the log database, graph bindings, and replay offsets retain the master
contract under `cpmeta.rdfLog` and `cpmeta.instanceServers`. There are no parallel
`rdfStore.rdfLog`, `rdfStore.rdfLogs`, or `rdfStore.rdfLogRestoreFromId` settings.

Only the standalone listener settings are new:

```hocon
rdfStore {
  httpBindInterface = "127.0.0.1"
  port = 9095
}
```

### Configuration resource layering (as of `docs/rdf-common-split/` tasks 14-16)

The application configuration resource is no longer owned solely by `rdfStore` and inherited by
`meta` through a build dependency - that dependency is gone (§ Status and objective). The
layering, from most to least specific, is:

1. JVM system properties (`-Dcpmeta.port=...`), or an explicitly named `-Dconfig.file`/
   `-Dconfig.resource`/`-Dconfig.url`.
2. `application.conf` in the JVM's working directory, if present and no explicit config property
   was given - the environment-specific file, kept out of version control.
3. Each application's own classpath `application.conf`, if it ships one (currently neither does;
   both rely on `reference.conf` defaults).
4. `reference.conf`, split by ownership:
   - `rdf-common/src/main/resources/reference.conf` is the single source for every default
     under `cpmeta` that both processes read: `core`, `citations.doi`, `rdfLog`, the shared
     `dataUploadService` fields, and `instanceServers`. Operator overrides therefore use the
     same paths in both processes and the packaged defaults cannot drift.
   - `rdfstore/src/main/resources/reference.conf` carries `rdfStore`-only defaults:
     its Akka configuration, `httpBindInterface`, `port`, `rdfStorage`, `sparql` throttling, and
     `citations` rendering policy. RDF-log restore bindings are derived from the shared
     instance-server configuration.
   - `meta`'s own `src/main/resources/reference.conf` carries its Akka configuration and every
     `cpmeta.*` key only `meta`'s `CpmetaConfig` reads (`onto`, `stationLabelingService`,
     `fileStoragePath`, `remoteRdfRepository`, meta-only `dataUploadService` fields, `auth`,
     `adminUsers`, `statsClient`, and meta's own `port`/`httpBindInterface`).

**Configuration model.** `CpmetaConfig` is `meta`'s alone and remains a single flat case class;
`rdfStore` parses its own narrow types (`RdfStoreConfig`, `SparqlServerConfig`,
`CitationStoreConfig`) instead. Their parsed types remain application-owned, but every
overlapping field uses the shared `cpmeta` key layout and defaults from rdf-common. rdfStore's
narrow readers ignore meta-only fields in those shared objects.
What is still shared is only what shared *code* takes as a parameter -- `DoiConfig`, the input to
`DoiClientFactory` -- plus `AppConfig`, which provides loading mechanics and no sections at all.
Operators override `cpmeta.rdfLog`; both processes parse that same path from their independently
packaged configuration.

## Data migration and cutover

1. **Baseline:** run the existing SPARQL/upload/landing-page suites against the last embedded release.
2. **Snapshot:** stop metadata writers in the embedded deployment; take both an RDF store snapshot and PostgreSQL log checkpoint.
3. **Seed:** restore the snapshot onto the volume owned by `rdfStore`. Never copy a live LMDB directory without the store's supported snapshot procedure.
4. **Shadow reads:** start `rdfStore` with no metadata writers running, and compare a corpus of SELECT/CONSTRUCT queries, named-graph counts, object pages, citations, and geospatial queries.
5. **Canary:** start one non-public `meta` instance against `rdfStore`; keep production writes stopped or on the old deployment.
6. **Write cutover:** stop writers, apply the final log delta, start one remote `meta` writer, then enable other stateless `meta` replicas.
7. **Observe:** compare update-log high-water marks, graph counts, query latency/error rates, and upload completion behavior.
8. **Retire embedded deployment:** remove the RDF volume from the old `meta` deployment only after the rollback window expires.

Rollback before new remote writes means redeploying the prior embedded release. After remote writes begin, first stop writers and replay/export the remote delta back to the embedded store; never start the old store from a stale snapshot.

## Verification gates

- Same tuple and graph results for a fixed regression-query corpus.
- Same named-context statement counts and representative statement hashes.
- Upload, update, versioning, collection creation, and completion tests pass remotely.
- Landing pages and RDF content negotiation match embedded mode.
- Labeling and ontology editors retain their read-after-write behavior.
- Concurrent updates to the same object remain serialized by `UploadLock` and store transactions.
- Failure tests cover timeout, unavailable store, partial response, duplicate mutation, and restart during write.
- Query throughput and p95/p99 latency meet current production baselines, including geospatial queries.

## Remaining implementation slices

1. Add service authentication to the private update and administration routes before exposing them across hosts. **Open.**
2. Consider idempotent mutation batches/outbox handling if cross-store atomicity needs stronger guarantees. **Open.**
3. Add remote integration tests using LMDB in addition to the current MemoryStore protocol test. **Partially implemented** (`docs/rdf-common-split/19-remote-integration-test.md`): a genuine cross-process suite forks a real `rdfStore` process against a temporary LMDB directory and a throwaway PostgreSQL cluster, driven with the same RDF4J `SPARQLRepository` class `meta` uses in production. It covers reads (tuple/graph/boolean queries and content negotiation across all supported formats), unlogged writes, and read-after-write. It does **not** yet cover logged writes (`/logged-update` double-append protection), custom-index/geospatial parity against an embedded baseline, or failure-mode chaos testing beyond a basic health-check timeout - those remain follow-up work, tracked in the task file's scope notes.
4. Split the shared configuration/domain support currently compiled in `rdfStore` into a smaller neutral library if independent publication is needed; this must preserve the current one-way dependency graph. **Implemented**, as the `rdfCommon` module (`docs/rdf-common-split/`, tasks 01-23): leaf utilities, domain exceptions, the RDF access API, vocabularies, the citation/DOI stack, and the read-side object fetchers moved there, and `meta`'s build-time dependency on `rdfStore` was removed (task 21) and CI-guarded against returning (task 22). See `docs/rdf-common-split/README.md` for the full task-by-task record.
