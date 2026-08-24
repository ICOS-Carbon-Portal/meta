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

Progress: **23 / 23 complete.** Tick a box when the task's own verification section passes, and
update the count above.

Further design work: [25 — Minimize rdfStore's citation graph configuration](25-minimize-rdfstore-citation-graph-config.md)
completed the direct store-owned `rdfStore.citationGraphs` model, removing rdfStore's
instance-server-shaped configuration view. The document also records endpoint and materialization
alternatives that were deliberately not adopted.

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
- [x] [15](15-split-config.md) — Split the configuration three ways
- [x] [16](16-meta-appconfig.md) — Give `meta` its own `AppConfig`

**Note on task 15:** done, in the end, across a few separate passes rather than in one shot.
`CpmetaConfig.scala` moved back out of `rdfCommon` into `meta` (`src/main/scala/CpmetaConfig.scala`),
since nothing outside `meta` needs its full shape any more. `rdfStore` no longer calls `meta`'s
`ConfigLoader.default` at all: `rdfstore/StoreConfig.scala`'s `RdfStoreConfigLoader` parses its own
narrow views instead — `CitationStoreConfig` (`core`, `citations`, `instanceServers`,
`dataUploadService`'s `metaServers`/`collectionServers`/`documentServers`/`handle`, next to
`CitationProvider`, its only consumer) and `SparqlServerConfig` (`rdfStore.sparql`, for query
throttling). That narrowing is what task 16's `reference.conf` split had been waiting on: the
`cpmeta.*` keys only `meta`'s own `CpmetaConfig` reads (`onto`, `stationLabelingService`,
`fileStoragePath`, `remoteRdfRepository`, `dataUploadService.etc`, `auth`, `adminUsers`, `statsClient`, meta's own
`port`/`httpBindInterface`) have moved out of `rdf-common/src/main/resources/reference.conf` into
`meta`'s own `src/main/resources/reference.conf`, which was an empty placeholder until now.
`cpmeta.core` and the shared DOI endpoint/member credentials in `cpmeta.citations` stayed in
`rdf-common` at that point (they have since been duplicated into both applications too - see the
second follow-up pass below); citation rendering style, cache warm-up and request timeout are owned by
`rdfStore.citations`. `dataUploadService` is application-owned:
meta carries the complete Handle.net client view, while rdfStore carries only `prefix` and
`baseUrl` at the backward-compatible `handle` path needed by `PidFactory`. `cpmeta.instanceServers` is
duplicated deliberately: meta owns the complete configuration, while rdfStore owns only the
write/read contexts, data-object graph definitions, and `cpMetaInstanceServerId` needed by
`CitationProvider`.

**Follow-up pass:** `rdf-common`'s `reference.conf` was audited key by key against what each app
actually parses, and now holds only genuinely shared defaults. What moved out:

| moved | to | why it isn't shared |
| --- | --- | --- |
| `rdfStore.sparql`'s throttling knobs (`maxQueryRuntimeSec`, quotas, `banLength`, …) | `rdfstore` | only `SparqlServerConfig` reads them |
| `cpmeta.adminUsers` | `meta` | only meta authorizes its administrative and labeling-email routes with it |
| `akka.http.caching.lfu-cache` | `rdfstore` | `rdfstore/SparqlRoute.scala` is the only `LfuCache` user |
| `cpmeta.rdfLog` + the `rdfStore.rdfLog` fallback it substitutes from | `meta` | only meta's `CpmetaConfig` parses it (→ `MetaDb`/`PostgresRdfLog`); the fallback exists solely to make that substitution resolvable off meta's classpath, so it belongs next to it |
| complete `cpmeta.instanceServers` | `meta` | rdfStore has an independent minimal read-side copy |
| `cpmeta.rdfStorage` + the `rdfStore.rdfStorage` fallback | deleted | dead: `CpmetaConfig` dropped its `rdfStorage` field, and `rdfStore` reads `rdfStore.rdfStorage`, not the `cpmeta` alias |
| all remaining `akka` defaults | both applications | runtime policy belongs to each independently deployable service; the values currently match but can now evolve independently |
| `cpmeta.dataUploadService` | both applications | meta owns the complete upload view; rdfStore owns only `handle`, and does not receive meta's server mappings or ETC settings |

**Second follow-up pass:** `rdf-common` now ships *no* `reference.conf` and defines *no*
application configuration section. The last shared defaults (`cpmeta.core` and the
`cpmeta.citations.doi` endpoint/member credentials) are duplicated verbatim into both
applications' own `reference.conf`, and the config data types followed:

| moved | to | why it isn't shared |
| --- | --- | --- |
| `RdflogConfig`, `DbServer`, `DbCredentials` (and their JSON formats) | both applications | each app parses its *own* section (`cpmeta.rdfLog` vs `rdfStore.rdfLog`) into it, and the `persistence/postgres` code reading them is already duplicated per app |
| `CitationConfig` (the `cpmeta.citations` wrapper) | both applications | a one-field wrapper over `DoiConfig`; defining it per app lets either extend its own citations section |
| `HandleNetClientConfig` | `meta` | only `meta` mints PIDs against handle.net; rdfStore's `PidFactory` takes plain values, and rdfStore parses its own narrow `HandleConfig` |
| `DataObjectInstServersConfig`, `DataObjectInstServerDefinition` | `meta` | rdfStore replaced its read-side copy with `CitationGraphsConfig` |

`SharedConfig.scala` is gone as a result; `CitationConfig.scala` became `DoiConfig.scala`, holding
just `DoiConfig`/`DoiConfigJsonProtocol`. `DoiConfig` stays shared because shared *code* takes it
as a parameter (`DoiClientFactory`, used by meta's `DoiService` and rdfStore's `CitationClient`),
not because both apps parse a common section. `AppConfig` also stays: it is loading mechanics
(the layering of system properties / working-dir `application.conf` / classpath), with no
knowledge of any section.

`rdf-common/src/test/resources/reference.conf` (a stub for the `rdfStore.*` keys rdf-common's own
`reference.conf` used to cross-reference) went away with the last of those cross-references.
`instanceServers` has subsequently moved out as well: both applications now carry explicit,
independently parseable views, and rdfStore's copy contains no journalling or ingestion settings.

Verified: `sbt rdfCommon/compile
rdfStore/compile meta/compile`, `rdfStore/Test/test` (229 tests, including `RouteTest`) and
`meta/Test/compile` all green (and, after the second pass, `metaCore/test`, `rdfCommon/test` and
`-Werror` compilation of all four modules plus `tools`); `se.lu.nateko.cp.meta.ConfigLoader.default` (in `meta/console`) and
`se.lu.nateko.cp.meta.RdfStoreConfigLoader.{citationStoreConfig,sparqlConfig,default}` (in
`rdfStore/console`) each resolve their full, correct views independently, confirming `cpmeta.*` no
longer needs to be reachable in its entirety from `rdfStore`'s classpath.

### Phase 5 — tests

- [x] [17](17-move-store-tests.md) — Move store-owned tests out of `src/test`
- [x] [18](18-move-shared-tests.md) — Move shared-code tests to `rdfCommon`
- [x] [19](19-remote-integration-test.md) — Add a remote integration test on LMDB *(superseded —
      see the revision note below: this coverage was removed as low-value)*

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
scenarios respectively. The suite was tagged `tags.RemoteIntegration` (defined at the time in
`rdf-common/src/test/scala/tags/TagObjects.scala`, since deleted — see the revision below),
excluded from the default fast `Test/test`
run (needs `initdb`/`pg_ctl` on `PATH` and takes several seconds), and always run via the new
`remoteIntegrationTest` sbt task, which is wired into `cpDeployPreAssembly` so it can never be
skipped before a production build.

**Revision of task 19 (post-completion):** the cross-process harness above was removed outright,
with nothing put back in its place. `meta` forking a real `rdfStore` process plus a throwaway
PostgreSQL cluster made `meta`'s test suite responsible for standing up another application's
storage backend — the wrong side of the module boundary this whole split is trying to draw — and
it meant the suite never actually ran in CI (it was only reachable via the `remoteIntegrationTest`
task inside `cpDeployPreAssembly`, which CI never invokes).

A first replacement attempt built `InProcessSparqlEndpoint`: a hand-rolled SPARQL 1.1 Protocol
HTTP server in the test JVM, backed by an RDF4J in-memory `Sail`, driven by the same
`SPARQLRepository` client `MetaDb` uses in production. On reflection this didn't earn its keep:
the "server" on the other end was also RDF4J, executing queries/updates and serializing results
via RDF4J's own machinery — so the suite was mostly RDF4J testing RDF4J through a thin HTTP shim,
unable to catch a regression in RDF4J itself (not meta's problem) and not exercising `MetaDb.scala`
either (so it couldn't catch a regression there). It was deleted along with the harness.

What's actually left to verify is different from either attempt: `rdf-common`'s own
`transact`/`accessEagerly` helpers (`utils/rdf4j/package.scala`) work against any RDF4J
`Repository` and don't need HTTP or a real endpoint to test — an embedded `SailRepository` would
do, if that coverage is ever judged worth adding. `remoteIntegrationTest`, the
`rdfstore-test-classpath.txt` resource generator, and the `tags.RemoteIntegration` tag were all
removed as dead weight along with the harness. LMDB-specific behavior (the fresh-store read-only
restart, Postgres-backed log replay) is `rdfStore`'s own implementation detail and was never
`meta`'s to test in the first place; it belongs in a test living in `rdfStore`'s own suite.

**Follow-up on the SPARQL route tests:** dropping `meta`'s dependency on `rdfStore` for local
query tests also deleted `test/services/sparql/SparqlRouteTests.scala`, whose subject
(`SparqlRoute`) had moved to `rdfStore`. Only its two biblio-info cases were carried over, into
`QueryTests` as plain SPARQL-level assertions. Of the rest, CORS and cache MISS/HIT were already
covered by `rdfstore/RouteTest.scala` and the `CancellationException` → `BadRequest` mapping by
`SparqlFailureHandlerTest`, but the end-to-end query-timeout case — the one that had carried the
`SlowRoute` tag — was left uncovered and has since been restored in `rdfstore/RouteTest.scala`.
It posts a three-way cross product under a `maxQueryRuntimeSec = 1` config and asserts a
`BadRequest`. The query's filter must be both unpushable and unsatisfiable so that nothing
streams out before the timeout: `QuotaManager.keepRunningIndefinitely` lets a query that has
already begun streaming outlive its deadline, which would make the assertion vacuous. It is not
tagged — at ~1.2s it does not need excluding from fast runs, and no build config ever filtered
on `SlowRoute` anyway.

### Phase 6 — cut the edge and ship two applications

- [x] [20](20-rdfstore-assembly-deploy.md) — Give `rdfStore` assembly and deploy configuration
- [x] [21](21-remove-dependson.md) — Delete `dependsOn(rdfStore)` from `meta`
- [x] [22](22-ci-guard.md) — Add a CI guard against the dependency returning
- [x] [23](23-update-split-doc.md) — Update `rdf-store-split.md`

**Note on task 20:** `cpDeployTarget := "cpmetardfstore"` is a proposed name, chosen only to be
distinct from `meta`'s `"cpmeta"` as the task file requires. It has not been confirmed against the
real Ansible inventories/playbooks (not visible from this repository), and `cpDeployPlaybook :=
"rdfstore.yml"` is a new playbook name that does not exist yet — whoever owns the deploy
infrastructure should confirm both, and write the actual playbook (with the RDF volume mount),
before this is ever used for a real deploy. Verified locally: `rdfStore/assembly` produces a fat
jar, and `java -jar` on it boots against a throwaway Postgres and temp LMDB dir, serving `/health`
with 200.

**Note on task 21:** compiled clean on the first try after moving exactly one file —
`CitationProviderFactory.scala` (config → `CitationProvider` glue depending only on already-shared
`CpmetaConfig`/`RdfLenses`/`CitationProvider`, no storage internals) — from `rdfstore/` to
`rdf-common/`, confirming the rest of tasks 02–19 had already drawn the line correctly. Cutting
the dependency surfaced one further issue, invisible at compile time: `rdf-common`'s own
`reference.conf` had `cpmeta.rdfLog = ${rdfStore.rdfLog}` (and `rdfStorage`), a substitution whose
source keys lived only in `rdfstore`'s `reference.conf` — fine while `meta` still carried that file
transitively, broken the moment it didn't, since Typesafe Config requires every `reference.conf`
to resolve on its own. Fixed by duplicating literal fallback defaults for `rdfStore.rdfLog`/
`rdfStore.rdfStorage` into `rdf-common`'s own `reference.conf` (see the comments there and in
`rdfstore`'s copy) rather than by touching `CpmetaConfig`'s shape — a real fix, not a workaround,
and one that would have surfaced the first time anyone tried to boot `meta` standalone post-cut.
Also required a second real fix: the task-19 remote-integration harness
(`RemoteRdfStoreHarness.currentClasspath()`) used to recover `rdfStore`'s runtime classpath by
walking the current (meta test) JVM's classloader chain, which only worked because `meta`
transitively carried `rdfStore` on its classpath; post-cut that's gone, so `meta`'s `build.sbt` now
generates a `rdfstore-test-classpath.txt` test resource from `rdfStore`'s own
`Compile / fullClasspath`, which the harness reads first. Verified: `sbt clean compile
Test/compile` and `sbt test` green across every module (`metaCore`, `rdfCommon`, `rdfStore`,
`meta`, `tools`, `uploadgui/fullOptJS`); `sbt "show meta/Compile/dependencyClasspath"` has no
`meta-rdf-store` entry; the task-19 remote integration test passes (10/10) with the classpath fix;
both `rdfStore` and `meta` were started together as separate forked JVMs against a real, restart-
cycled LMDB store and a throwaway Postgres, and `meta` served `/buildInfo` (200) while reading
real triples through `rdfStore` over HTTP — all temp dirs/Postgres clusters/processes cleaned up
afterward. `sbt assembly` (which `IcosCpSbtDeployPlugin` binds to run the whole
`cpDeployPreAssembly` sequence, including `remoteIntegrationTest`) passes through that entire
sequence and only then fails at the pre-existing `frontendBuild` step, because this sandbox has no
`npm`/`node` installed — an environment gap unrelated to the module split, not a regression from
this change.

**Note on task 22:** `checkModuleBoundaries` compares each project's own `Compile / classDirectory`
against the other's `Compile / dependencyClasspath`, rather than matching classpath entry file
names as the task file's sketch does: same-build project dependencies show up on the classpath as
a `target/scala-.../classes` directory, and every module's is literally named `classes`
(indistinguishable by name alone); worse, this repository's own path contains a directory
literally called `meta` (`.../bulk/meta/rdf-separate-service`), so a substring match on `"meta"`
would false-positive on every single classpath entry. Comparing canonical class-directory paths
avoids both traps. Verified: passes on the current tree; temporarily re-adding `rdfStore` to
`meta`'s `dependsOn` makes it fail with `"meta must not depend on rdfStore, but rdfStore's class
directory is on meta's Compile classpath: .../rdfstore/target/scala-3.3.4/classes"`; reverted.
Wired into the GitHub Actions workflow and both projects' `cpDeployPreAssembly`. The optional
grep-based source guard is also added (CI-only, `.github/workflows/scala.yml`), scoped to actual
`import` statements rather than any string occurrence — `RemoteRdfStoreHarness.scala` legitimately
references `"se.lu.nateko.cp.meta.rdfstore.Main"` as a class-name string (to fork it as a separate
process) without importing the package, which a naive grep would have flagged.

### Phase 7 — tighten the boundary

Found by auditing, after the split shipped, which `rdfCommon` and `metaCore` declarations each
application actually references. Nothing here is required for the split to work; each item is a
piece of one application's domain, or one application's build, still sitting on the shared side.

- [x] [26](26-decouple-meta-deploy-gate.md) — Stop running `rdfStore`'s tests in `meta`'s deploy gate
- [x] [27](27-drop-unused-test-dependencies.md) — Drop the unused `rdfCommon % "test->test"` dependencies
- [x] [28](28-delete-cachedsource-tests.md) — Delete `CachedSourceTests`
- [ ] [29](29-unshare-meta-only-exceptions.md) — Move the meta-only exceptions out of `rdfCommon`
- [ ] [30](30-unshare-single-app-members.md) — Move single-application members out of shared files
- [ ] [31](31-prune-rdfcommon-dependencies.md) — Prune `rdfCommon`'s unused library dependencies
- [ ] [32](32-move-index-algo-to-rdfstore.md) — Move `core/algo` into `rdfStore` *(published-library risk — see the task file)*
- [ ] [33](33-readonly-conn-for-citation-provider.md) — Give `CitationProvider` a read-only connection

What deliberately stays shared, and why: the metadata-reading stack
(`CpmetaReader`/`DobjMetaReader`/`CollectionReader`/`StaticObjectReader`/`AttributionProvider`/
`CpVocab`/`CpmetaVocab`/`RdfLens`, ~1,700 lines) is used substantively by both and must produce
identical `core.data` DTOs on both sides — duplicating it is the coupling risk, not the fix.
`services/derived/DerivedMetadata.scala` is the wire contract *between* the two services.
`DoiConfig` is a parameter type of shared code (`DoiClientFactory`). `AppConfig` is loading
mechanics with no knowledge of any config section.

## Out of scope

Service authentication on `/logged-update` and `/admin/read-only`, and idempotent mutation
batches (slices 1 and 2 in `rdf-store-split.md`) are orthogonal to the module split and can
proceed in parallel.
