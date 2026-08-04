# Splitting the RDF store from `meta`

## Status and objective

The target is two independently deployable JVM applications:

1. **`rdfStore`** exclusively owns the RDF4J Sail, its LMDB/NativeStore files, query execution, and the SPARQL query/update protocol.
2. **`meta`** owns metadata-domain behavior (upload validation, RDF production, landing pages, labeling, ontology editors, citations, DOI integration, and metadata flows) and accesses RDF through an RDF4J `Repository` client. It has no RDF-log configuration or implementation awareness.

The process and build split is implemented. `rdfStore` depends only on `metaCore`, owns the RDF implementation and runtime configuration, and provides `/sparql`, `/update`, and `/health`. The dependency direction is `meta -> rdfStore -> metaCore`; `rdfStore` does not depend on the `meta` application. `meta` always connects through RDF4J `SPARQLRepository` and has no embedded-store fallback.

## Existing coupling

Before the split, `MetaDbFactory` performed four distinct jobs:

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

```text
public clients
     |
     v
+---------------- meta ----------------+
| public /sparql compatibility proxy   |
| upload, editors, labeling, landing   |
| metadata flows, DOI/citation         |
| RDF4J SPARQLRepository client        |
+------------------+-------------------+
                   | private network
              query| SPARQL 1.1 |update
                   v
+-------------- rdfStore --------------+
| /sparql (read), /update (write)       |
| quota, timeout, serialization         |
| custom indexes and change listeners  |
| RDF4J SailRepository                  |
| LMDB/NativeStore filesystem owner     |
+---------------------------------------+
                   |
             persistent volume
```

Only `rdfStore` may mount the RDF storage directory. `meta` must not have access to that volume. The public `/sparql` URL can remain on `meta`; it executes against `SPARQLRepository`, preserving the public address while the private service topology changes. It may later become a reverse-proxy route directly to `rdfStore`.

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

RDF4J `SPARQLRepository` sends `RepositoryConnection.add/remove` and prepared updates to the configured update endpoint. The private `/update` endpoint executes SPARQL Update in a repository transaction. Named contexts remain authoritative: each logical `InstanceServer` has its configured read contexts and exactly one normal write context.

The update endpoint must never be public. Production should use service identity (mTLS or a short-lived workload token) in addition to network policy. The initial implementation binds to loopback and relies on network isolation; authentication is a required hardening item before cross-host deployment.

### Why not a bespoke metadata API

A DTO-level RDF API would duplicate RDF4J query, value, context, and transaction semantics and force broad changes through the metadata code. The RDF4J repository client preserves the existing programming model. Domain APIs remain in `meta`; the storage boundary deals only in SPARQL/RDF.

## Ownership rules

| Concern | Owner |
|---|---|
| LMDB/NativeStore files and lifecycle | `rdfStore` |
| SPARQL parsing, execution quotas, cancellation, result formats | `rdfStore` |
| Carbon Portal custom query indexes | `rdfStore` |
| Store backups, compaction, read-only maintenance | `rdfStore` |
| RDF-log storage, graph replay, and change history | `rdfStore` |
| Upload validation and RDF statement production | `meta` |
| Ontology-driven editors and labeling workflow | `meta` |
| DOI, citation, landing-page composition | `meta` |
| Public authentication/authorization | `meta` |

## Transactions and RDF update logs

`rdfStore` wraps the Sail connection and records asserted/retracted statements by named graph. It appends each transaction's changes to the graph's PostgreSQL RDF log immediately before committing the RDF4J transaction, preserving the former local ordering while removing all logging behavior from `meta`. Rollbacks are not logged, and logging stays disabled during startup replay so restored rows are not appended again.

The two persistence systems still cannot provide a distributed atomic commit: an RDF4J commit failure after a successful PostgreSQL append can leave a log entry ahead of the store. An idempotent mutation/outbox design remains a possible hardening step, but it is no longer part of `meta`.

## Custom index migration

`CpNotifyingSail`, `IndexHandler`, `GeoIndexProvider`, storage selection, query evaluation, citation-backed statement enrichment, read-only switching, and index snapshots now live in and execute inside `rdfStore`. The module depends on `metaCore` for shared data types and algorithms, never on `meta`.

Migration sequence:

1. Storage, indexes, SPARQL execution, RDF access helpers, vocabularies, and enrichment code are compiled by `rdfStore` (implemented).
2. Indexes are restored or rebuilt in `rdfStore` before its HTTP listener is bound (implemented).
3. The embedded Sail/index lifecycle has been deleted from `meta` (implemented).
4. Index dump/read-only control is exposed on the loopback-bound administration route (implemented); add service authentication before exposing it across hosts.

Queries sent through remote mode are evaluated by the same `CpNotifyingSail` and custom indexes in `rdfStore`. Performance and Carbon Portal-specific query rewrites must still be regression-tested across the new HTTP hop before production cutover.

## Startup and readiness

`rdfStore` startup order:

1. open and initialize the Sail repository;
2. on a fresh store, restore each configured RDF log sequentially;
3. restore or build custom indexes;
4. enable transaction logging and leave the freshly built store read-only;
5. expose the routes so operators can verify the restore, then restart `rdfStore` for normal indexed operation.

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
  queryEndpoint = "http://127.0.0.1:9095/sparql"
  updateEndpoint = "http://127.0.0.1:9095/update"
  adminEndpoint = "http://127.0.0.1:9095/admin/read-only"
  historyEndpoint = "http://127.0.0.1:9095/history"
}
```

The standalone application owns `rdfStore.rdfStorage`, `rdfStore.rdfLog`, the graph-to-log mappings, and RDF-log replay offsets. It currently reuses only the general `cpmeta.sparql` query limits and adds:

```hocon
rdfStore {
  httpBindInterface = "127.0.0.1"
  port = 9095
}
```

The application configuration resource is owned by `rdfStore` and is inherited by `meta` through the build dependency. Deployment-specific overrides continue to use the same HOCON keys.

## Data migration and cutover

1. **Baseline:** run the existing SPARQL/upload/landing-page suites against the last embedded release.
2. **Snapshot:** stop metadata writers in the embedded deployment; take both an RDF store snapshot and PostgreSQL log checkpoint.
3. **Seed:** restore the snapshot onto the volume owned by `rdfStore`. Never copy a live LMDB directory without the store's supported snapshot procedure.
4. **Shadow reads:** start `rdfStore` read-only and compare a corpus of SELECT/CONSTRUCT queries, named-graph counts, object pages, citations, and geospatial queries.
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

1. Add service authentication to the private update and administration routes before exposing them across hosts.
2. Consider idempotent mutation batches/outbox handling if cross-store atomicity needs stronger guarantees.
3. Add remote integration tests using LMDB in addition to the current MemoryStore protocol test.
4. Split the shared configuration/domain support currently compiled in `rdfStore` into a smaller neutral library if independent publication is needed; this must preserve the current one-way dependency graph.
