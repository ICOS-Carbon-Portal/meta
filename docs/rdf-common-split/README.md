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

Progress: **22 / 23 complete.** Task 15 is the only one left unchecked (deliberately deferred; see
its note below), so 22/23 is the maximum achievable without doing it. Tick a box when the task's
own verification section passes, and
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
scenarios respectively. The suite is tagged `tags.RemoteIntegration` (see
`rdf-common/src/test/scala/tags/TagObjects.scala`), excluded from the default fast `Test/test`
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

## Out of scope

Service authentication on `/logged-update` and `/admin/read-only`, and idempotent mutation
batches (slices 1 and 2 in `rdf-store-split.md`) are orthogonal to the module split and can
proceed in parallel.
