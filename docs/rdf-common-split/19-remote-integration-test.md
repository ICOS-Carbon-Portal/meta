# 19 — Add a remote integration test on LMDB

**Phase:** 5 — tests
**Depends on:** 17
**Blocks:** 21

## Goal

Test `meta` against a real `rdfStore` process backed by LMDB, not just the current MemoryStore
protocol test.

## Why

This is open slice 3 in `rdf-store-split.md:197`, and it is the gate for the whole split. Every
other task in this plan is a compile-time refactor; this is the only thing that verifies the
two applications actually work together over HTTP against the production storage backend.

The existing coverage is thinner than it needs to be:

- `rdfstore/src/test/scala/rdfstore/RouteTest.scala` — protocol-level, MemoryStore
- `rdfstore/src/test/scala/rdfstore/SparqlFailureHandlerTest.scala` — error paths
- `src/test/scala/test/services/sparql/regression/*` — embedded store, in-process

None of them exercise `meta` -> HTTP -> `rdfStore` -> LMDB.

## Scope

Cover the verification gates listed in `rdf-store-split.md:184-191`:

1. **Reads** — `SPARQLRepository` in quad mode: `prepareTupleQuery`, `prepareGraphQuery`,
   `getStatements`, `hasStatement` against `/internal/sparql`; tuple results as SPARQL
   JSON/XML/CSV/TSV, `ASK` as JSON/XML, graph results as RDF/XML and Turtle.
2. **SPARQL writes** — `RepositoryConnection.add`/`remove` and prepared updates via
   `/internal/sparql`, verifying named-context targeting: each logical `InstanceServer`
   has its configured read contexts and exactly one write context.
3. **Meta-owned logged writes** — an `InstanceServer.applyAll` batch through the same SPARQL
   endpoint, verifying Meta's PostgreSQL append happens before the RDF4J commit and that
   rdfStore replay does not append again.
4. **Read-after-write** — labeling and the ontology editor depend on it
   (`rdf-store-split.md:188`); the HTTP hop is where this can regress.
5. **Custom-index correctness across the hop** — a `DataObjectFetch`-shaped query and a
   geospatial query returning identical results to the embedded baseline.
6. **Failure modes** — timeout, store unavailable, partial response, duplicate mutation,
   restart during write.

## Mechanics

- Start `rdfStore` as a real process on a temporary LMDB directory, bound to a free loopback
  port; tear the directory down afterwards. `TestDb` in the regression suite already has
  LMDB-on-temp-dir setup code worth reusing (`LmdbConfig`, `RdfStorageConfig`).
- Point `meta`'s `cpmeta.remoteRdfRepository` at it (`rdf-store-split.md:146-154`).
- Wait on `/health` before running assertions; `rdfStore` leaves a freshly built store
  read-only (`rdf-store-split.md:127`), so the fixture must account for the restart step or
  seed a store that is already indexed.
- Tag the suite so it can be excluded from fast local runs but is mandatory in CI —
  `src/test/scala/test/tags/TagObjects.scala` already provides the tagging mechanism.
  *(Superseded: the harness this plan describes was built and then removed — see the revision
  note in `README.md`. `TagObjects.scala` was deleted along with it; no scalatest `Tag` object
  survives in the build. The one remaining tag is the `tags.DbTest` annotation, now at
  `rdfstore/src/test/scala/tags/DbTest.java`.)*

## Verification

- The suite passes in CI on a clean checkout.
- Deliberately stopping `rdfStore` mid-suite produces a clear, non-hanging failure.
- Add it to `cpDeployPreAssembly` (`build.sbt:161-169`) so it cannot be skipped before a
  production build.
