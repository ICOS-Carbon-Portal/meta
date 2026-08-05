# 18 — Move shared-code tests to `rdfCommon`

**Phase:** 5 — tests
**Depends on:** 02, 04, 05, 10
**Blocks:** 21

## Goal

Give the code moved into `rdfCommon` its tests, so the shared module is verifiable on its own
and `meta`'s test suite stops covering code it no longer owns.

## Files to move to `rdf-common/src/test/scala/`

| File | Covers |
|---|---|
| `test/api/CustomVocabTests.scala` | `CustomVocab`, `UriId` (task 10) |
| `test/api/HandleNetClientTests.scala` | `HandleNetClient` (task 05) |
| `test/services/CpVocabTests.scala` | `CpVocab` (task 10) — see caveat |
| `test/instanceserver/InstanceServerTests.scala` | `InstanceServer` (task 04) |
| `test/utils/DateTimeUtilsTests.scala` | `utils` (task 02) |
| `test/utils/UrlEncodeDecodeTests.scala` | `utils.urlEncode` (task 02) |
| `test/utils/JavaTaskCancellationTests.scala` | `utils.async` (task 02) |
| `test/utils/rdf4j/EnrichedUriTests.scala` | `utils.rdf4j` (task 02) |
| `test/utils/streams/CachedSourceTests.scala` | check owner — see below |

Plus the fixtures under `src/test/resources/crypto/` that `HandleNetClientTests` uses.

## Caveats

- **`CpVocabTests`** — task 08 moved the TC-scoped members to `TcVocab` in `meta`. Split the
  suite: assertions about the relocated members stay in `meta` alongside `TcVocab`; the rest
  moves. These tests guard persistent URI strings, so do not drop any assertion in the split.
- **`CachedSourceTests`** — `utils/streams/ZipEntryFlow.scala` is `meta`-only and stays put.
  Check whether `CachedSource` is in `rdfCommon`'s `utils` or `meta`'s `utils/streams` before
  moving this one.
- **`test/tags/TagObjects.scala`** — scalatest tag definitions, probably referenced from suites
  in several modules. Either duplicate it or put it in `rdfCommon`'s test tree and give the
  other modules a `dependsOn(rdfCommon % "test->test")`.
- **`test/TestConfig.scala` and `test/MetaTestFactory.scala`** — shared test helpers. Determine
  which modules' suites need them; `metaCore` already uses a `test->test` dependency
  (`build.sbt:110`), so the pattern exists in this build.

## Stays in `meta`

`test/InstOntoTests`, `test/OntoTests`, `test/OwlOntologyTest`, `test/reasoner/*`,
`test/routes/*`, `test/icos/*`, `test/metaflow/*`, `test/ingestion/*`,
`test/services/upload/*` (including `geocov/`), `test/utils/streams/ZipEntryFlowTests`,
`test/utils/SparqlClient`, `KmlGeoJsonWorkbench`, and everything under `src/test/scala/upload/`
(those are operator workbench scripts, not tests).

## Steps

1. `git mv` the files listed above.
2. Add `scalatest` and `scalacheck` to `rdfCommon`'s `libraryDependencies` under `Test`.
3. Add `rdfCommon % "test->test"` dependencies where shared test helpers require it.
4. Move the associated resources.

## Verification

- `sbt rdfCommon/test`, `sbt rdfStore/test` and `sbt meta/test` all pass.
- Total test count across the three modules matches the pre-move total (minus any assertions
  deliberately consolidated in the `CpVocabTests` split — account for those explicitly).
