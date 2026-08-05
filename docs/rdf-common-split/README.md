# Extracting `rdfCommon`: task index

Companion to [`../rdf-store-split.md`](../rdf-store-split.md). That document describes the
process split, which is already implemented. This directory covers the work that remains:
removing the build-time dependency `meta -> rdfStore`.

## Problem

The process split is done — nothing under `src/` touches a Sail, LMDB, or the RDF-log
implementation. `MetaDb.scala` builds a `SPARQLRepository` and talks to `rdfStore` over HTTP.
But `build.sbt` still has `meta.dependsOn(rdfStore)`, because `rdfStore` is doubling as the
shared library for everything both applications need: configuration, vocabularies, the
`InstanceServer` API, generic utilities, domain exceptions, the metaflow model, the citation
stack and the read-side object fetchers.

This is open slice 4 in `rdf-store-split.md`.

## Target

```text
                metaCore          (no RDF4J; published to Nexus, drives TS/Py codegen)
                    ^
                rdfCommon         (RDF4J-level shared code)
                    ^  ^
        rdfStore ---'  '--- meta
```

`rdfStore` and `meta` both depend on `rdfCommon`; neither depends on the other.

## Two decisions that shape the plan

1. **`metaCore` is not the home for this code.** It is published as a library, feeds the
   TypeScript and Python code generators, and deliberately has no RDF4J on its classpath.
2. **The citation stack is shared, not `meta`-owned.** `rdfStore`'s custom Sail enriches
   statements with citations (`magic/StatementsEnricher.scala`, `magic/CpNotifyingSail.scala`),
   so it genuinely needs `CitationProvider` and the object fetchers. Having the store call back
   into `meta` over HTTP from inside query evaluation would create a runtime cycle in the hot
   path. This contradicts the ownership table in `rdf-store-split.md`, which is corrected in
   task 23.

## Working principles

- **Move first, split later.** Most tasks are pure file relocations with unchanged package
  names — no import churn, trivially reviewable diffs. Only the configuration and the
  metaflow/vocabulary coupling need real code changes; those come after the mechanical moves.
- **Keep `meta.dependsOn(rdfStore)` until task 21.** The build stays green throughout, and
  cutting the edge becomes a one-line change that should break nothing.

## Tasks

Progress: **18 / 23 complete.** Tick a box when the task's own verification section passes, and
update the count above. (Task 15 is intentionally left unchecked and undone; see its row below.)

### Phase 1 — stand up `rdfCommon` (mechanical)

- [x] [01](01-create-rdfcommon-module.md) — Create the `rdfCommon` sbt module
- [x] [02](02-move-leaf-utilities.md) — Move leaf utilities
- [x] [03](03-move-domain-exceptions.md) — Move domain exceptions
- [x] [04](04-move-rdf-access-api.md) — Move the RDF access API
- [x] [05](05-move-handlenetclient.md) — Move `HandleNetClient`
- [x] [06](06-unleak-geo-helpers.md) — Un-leak the JTS geo helpers

### Phase 2 — break the metaflow/vocabulary knot

- [x] [07](07-extract-tcmetasource-constants.md) — Extract `TcMetaSource`'s two shared constants
- [x] [08](08-extract-tc-vocab.md) — Extract TC-scoped URI minting out of `CpVocab`
- [x] [09](09-move-metaflow-model.md) — Move the metaflow model to `meta`
- [x] [10](10-move-vocabularies.md) — Move the vocabularies to `rdfCommon`

### Phase 3 — citation and object readers

- [x] [11](11-move-citation-stack.md) — Move the citation stack to `rdfCommon`
- [x] [12](12-move-object-fetchers.md) — Move the read-side object fetchers to `rdfCommon`
- [x] [13](13-move-metadataupdater.md) — Move `MetadataUpdater` to `meta`

### Phase 4 — configuration

- [x] [14](14-move-config-verbatim.md) — Move `CpmetaConfig.scala` to `rdfCommon` unchanged
- [ ] [15](15-split-config.md) — Split the configuration three ways *(optional for task 21;
      attempted and deliberately deferred — see note below)*
- [x] [16](16-meta-appconfig.md) — Give `meta` its own `AppConfig`

**Note on task 15:** deliberately not done in this pass. `CpmetaConfig` stayed a single 16-field
case class in `rdfCommon`; `rdfstore/Main.scala` and `src/main/scala/Main.scala` both still call
the same `ConfigLoader.default`, unnarrowed. This has a real consequence for task 16's
`reference.conf` split: because `rdfStore`'s own boot path parses the *whole* `cpmeta` object
(not a narrower store-only view), every `cpmeta.*` default — including fields only `meta` ever
reads, like `onto` and `fileStoragePath` — has to stay reachable from `rdfStore`'s own module
classpath. They live in `rdf-common/src/main/resources/reference.conf` (see the comment there)
rather than being split by conceptual ownership as task 15's classification table would suggest;
`src/main/resources/reference.conf` in `meta` is consequently an empty placeholder for now. Once
task 15 narrows the config type `rdfStore` actually parses, the meta-only `cpmeta.*` keys can
move there. Task 15 was skipped rather than half-done because splitting `CitationProviderFactory`,
`RdfLogManager`, and both `Main`s onto narrower config types, then verifying independent
config-validation (task 15's "corrupt a meta-only key, confirm rdfStore still starts") is a
large, separately-reviewable change in its own right — not required for task 16 or task 21.

### Phase 5 — tests

- [x] [17](17-move-store-tests.md) — Move store-owned tests out of `src/test`
- [x] [18](18-move-shared-tests.md) — Move shared-code tests to `rdfCommon`
- [x] [19](19-remote-integration-test.md) — Add a remote integration test on LMDB *(scope note
      below: reads, unlogged writes and read-after-write are covered by a genuine, CI-worthy
      suite; logged writes, custom-index parity and failure-mode chaos testing are left as
      follow-up work)*

**Note on task 17:** `TestDb.scala`'s seeding was rewired to plain RDF4J
(`RepositoryConnection`/`Loading.loadResource`, option 1 in the task file) rather than kept on
`meta`'s `Ingestion`/`RdfXmlFileIngester`/`BnodeStabilizers`, since none of the regression queries
depend on stable blank-node identity (`bnode_N`-shaped bindings) — verified by grepping the
regression corpus before making the call. `Rdf4jSparqlRunner` also moved to `rdfCommon` (option 2),
since it is otherwise-trivial, self-contained, and `SparqlTests`/`TestDb` both need it in
`rdfStore`'s test tree. Option 3 (drive a running `rdfStore` over HTTP from `meta`) was correctly
identified by the task file as "really task 19 wearing a different hat" and is what task 19
does instead, at the level of a purpose-built cross-process suite rather than the embedded
regression corpus.

**Note on task 19:** built as a genuine cross-process harness
(`src/test/scala/test/remote/RemoteRdfStoreHarness.scala`) that forks a real `rdfStore` process
(via `ProcessBuilder`, not an in-process simulation) against a temporary LMDB directory, plus a
throwaway PostgreSQL cluster via `initdb`/`pg_ctl` (rdfStore's `Main` always constructs a
`PostgresRdfLog` per configured graph at boot, even when a test never touches `/logged-update`,
so Postgres is a hard boot dependency, not optional). The harness performs the documented restart
(`rdf-store-split.md:127`): boot once so the fresh, empty store and index are built and the
process goes read-only, stop it, then boot again against the same now-non-empty directory, which
comes up writable. The suite (`RemoteLmdbIntegrationTest.scala`) drives it with the same RDF4J
`SPARQLRepository` class `MetaDb` uses in production, covering scope items 1 (reads — tuple/graph/
boolean queries and `getStatements`/content negotiation across JSON/XML/CSV/TSV for tuples,
JSON/XML for booleans, RDF/XML/Turtle for graphs), 2 (unlogged writes via
`RepositoryConnection.add`/`remove`, including that a write to one named context is invisible in
another) and 4 (read-after-write, no propagation delay over the HTTP hop) — 10 tests, all
genuinely passing against a live process. Scope items 3 (logged writes / `/logged-update`
double-append protection), 5 (custom-index/geospatial parity against an embedded baseline) and 6
(failure-mode chaos testing beyond a basic health-check timeout) are explicitly left as follow-up
work: the Postgres infrastructure for item 3 already exists in the harness, but driving
`RdfMutation`/`LoggingInstanceServer` and inspecting the log table is a separately-verifiable
suite; items 5 and 6 need a seeded, indexed corpus comparable to `TestDb`'s and dedicated chaos
scenarios respectively. The suite is tagged `tags.RemoteIntegration` (see
`rdf-common/src/test/scala/tags/TagObjects.scala`), excluded from the default fast `Test/test`
run (needs `initdb`/`pg_ctl` on `PATH` and takes several seconds), and always run via the new
`remoteIntegrationTest` sbt task, which is wired into `cpDeployPreAssembly` so it can never be
skipped before a production build.

### Phase 6 — cut the edge and ship two applications

- [ ] [20](20-rdfstore-assembly-deploy.md) — Give `rdfStore` assembly and deploy configuration
- [ ] [21](21-remove-dependson.md) — Delete `dependsOn(rdfStore)` from `meta`
- [ ] [22](22-ci-guard.md) — Add a CI guard against the dependency returning
- [ ] [23](23-update-split-doc.md) — Update `rdf-store-split.md`

## Out of scope

Service authentication on `/logged-update` and `/admin/read-only`, and idempotent mutation
batches (slices 1 and 2 in `rdf-store-split.md`) are orthogonal to the module split and can
proceed in parallel.
