# Splitting the RDF store from `meta`

## Status and objective

The target is two independently deployable JVM applications:

1. **`rdfStore`** exclusively owns the RDF4J Sail, its LMDB/NativeStore files, query execution, and the SPARQL query/update protocol.
2. **`meta`** owns metadata-domain behavior (upload validation, RDF production, landing pages, labeling, ontology editors, citations, DOI integration, and metadata flows) and accesses RDF through an RDF4J `Repository` client.

The first migration slice is implemented in this repository. Embedded mode remains the default for a safe rollout; setting `cpmeta.remoteRdfRepository` switches `meta` to RDF4J `SPARQLRepository`. The new `rdfStore` sbt project provides `/sparql`, `/update`, and `/health`.

## Existing coupling

`MetaDbFactory` currently performs four distinct jobs:

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

Only `rdfStore` may mount the RDF storage directory. `meta` must not have access to that volume in remote mode. The public `/sparql` URL can initially remain on `meta`; it executes against `SPARQLRepository`, preserving the public address while the private service topology changes. It may later become a reverse-proxy route directly to `rdfStore`.

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
| RDF graph bootstrap/replay | one designated initializer, ultimately `rdfStore` |
| Upload validation and RDF statement production | `meta` |
| Ontology-driven editors and labeling workflow | `meta` |
| DOI, citation, landing-page composition | `meta` |
| Public authentication/authorization | `meta` |

## Transactions and RDF update logs

The existing `LoggingInstanceServer` appends PostgreSQL RDF-log records inside the callback used by a local repository transaction. That is not a distributed atomic transaction after the split. Pretending it remains atomic would create an undetectable split-brain failure mode.

The target write design is an **idempotent mutation batch**:

1. `meta` calculates a batch of asserted/retracted statements and assigns a mutation UUID.
2. `meta` submits the batch to a private `rdfStore` mutation endpoint.
3. `rdfStore`, as the write authority, records the mutation ID and RDF-log/outbox record, then commits RDF changes.
4. Repeating a completed mutation ID returns its prior result without applying it twice.
5. A background publisher may copy the authoritative outbox to PostgreSQL if PostgreSQL remains the long-term audit/rebuild store.

Until that endpoint is implemented, remote mode uses standard SPARQL Update. This preserves ordinary metadata behavior but does **not** claim atomicity between remote RDF and the legacy PostgreSQL log. Run a single `meta` writer, monitor both stores, and retain embedded mode as the rollback path during this phase.

## Custom index migration

`CpNotifyingSail`, `IndexHandler`, `GeoIndexProvider`, read-only switching, and index snapshots execute in-process with the Sail and therefore belong to `rdfStore`, not behind calls from `meta`. The standalone application now owns their runtime lifecycle, although their source code still comes from the `meta` artifact until the module extraction below is completed.

Migration sequence:

1. Move the storage/index packages into a neutral `rdf-store-core` project.
2. Move any citation-derived index enrichment behind an interface whose inputs are repository data and statement-change events.
3. Initialize and restore indexes in `rdfStore` before its HTTP listener is bound (implemented).
4. Put index dump/read-only controls on a private authenticated administration route.
5. Delete the embedded index lifecycle from `meta` after parity tests pass.

Queries sent through remote mode are evaluated by the same `CpNotifyingSail` and custom indexes in `rdfStore`. Performance and Carbon Portal-specific query rewrites must still be regression-tested across the new HTTP hop before production cutover.

## Startup and readiness

`rdfStore` startup order:

1. acquire an exclusive process/volume lock;
2. open and initialize the Sail repository;
3. restore or build custom indexes;
4. optionally run graph bootstrap/replay under an explicit initializer flag;
5. expose the update listener;
6. report ready only after query and update self-checks pass.

`meta` startup order in remote mode:

1. create `SPARQLRepository` from query/update URLs;
2. execute a bounded readiness query;
3. construct context-scoped instance servers and domain services;
4. do not restore local indexes or touch RDF storage files;
5. expose public routes only after required reference graphs are present.

Readiness should distinguish `live` (process responds) from `ready` (repository and indexes are usable). Include a store-generation identifier in readiness so operators can verify that all `meta` instances point to the intended store.

## Configuration

Embedded compatibility mode:

```hocon
cpmeta.remoteRdfRepository = null
```

Remote mode:

```hocon
cpmeta.remoteRdfRepository {
  queryEndpoint = "http://127.0.0.1:9095/sparql"
  updateEndpoint = "http://127.0.0.1:9095/update"
}
```

The standalone application currently reuses `cpmeta.rdfStorage` and `cpmeta.sparql` settings and adds:

```hocon
rdfStore {
  httpBindInterface = "127.0.0.1"
  port = 9095
}
```

Before final separation, move those shared settings to an `rdf-store-core` reference configuration so `rdfStore` no longer needs the complete `meta` configuration.

## Data migration and cutover

1. **Baseline:** deploy the code in embedded mode and run existing SPARQL/upload/landing-page suites.
2. **Snapshot:** stop metadata writers or switch embedded `meta` to read-only; take both an RDF store snapshot and PostgreSQL log checkpoint.
3. **Seed:** restore the snapshot onto the volume owned by `rdfStore`. Never copy a live LMDB directory without the store's supported snapshot procedure.
4. **Shadow reads:** start `rdfStore` read-only and compare a corpus of SELECT/CONSTRUCT queries, named-graph counts, object pages, citations, and geospatial queries.
5. **Canary:** configure one non-public `meta` instance with `remoteRdfRepository`; keep all writes on the embedded deployment.
6. **Write cutover:** stop writers, apply the final log delta, start one remote `meta` writer, then enable other stateless `meta` replicas.
7. **Observe:** compare update-log high-water marks, graph counts, query latency/error rates, and upload completion behavior.
8. **Retire embedded ownership:** remove the RDF volume from `meta` only after the rollback window expires.

Rollback before new remote writes is a configuration reversal. After remote writes begin, first stop writers and replay/export the remote delta back to the embedded store; never start the old store from a stale snapshot.

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

1. Extract storage/SPARQL/index code so `rdfStore` depends only on `meta-core` plus the extracted module, rather than on the complete `meta` artifact.
2. Add authenticated private update/admin routes.
3. Implement idempotent mutation batches and move RDF-log/outbox ownership to `rdfStore`.
4. Move graph bootstrap/replay and RDF-log ownership to `rdfStore`.
5. Add remote integration tests using both MemoryStore and LMDB.
6. Remove embedded mode, `rdfStorage` configuration, and storage dependencies from `meta` after production parity is established.
