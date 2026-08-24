# 27 — Drop the unused `rdfCommon % "test->test"` dependencies

**Phase:** 7 — tighten the boundary
**Depends on:** 18

## Goal

Remove `rdfCommon % "test->test"` from both `meta` (`build.sbt:136`) and `rdfStore`
(`build.sbt:293`).

## Why

A `test->test` dependency exists to share test *fixtures*. `rdf-common/src/test` contains eight
test suites and no fixtures: nothing defined there is referenced from `src/test` or
`rdfstore/src/test`, and `rdf-common/src/test/resources` is empty (it was emptied when the last
config cross-reference went away — see the README's follow-up notes).

Keeping the edge means both applications' test compilation waits on rdf-common's test
compilation, and it silently invites future fixture sharing that would re-couple the two.

`metaCore % "test->test"` on `meta` is a different matter and must stay: `src/test` uses
`TestFactory` from `core/src/test`.

## Steps

1. `meta`: `.dependsOn(metaCore, rdfCommon, metaCore % "test->test")`.
2. `rdfStore`: `.dependsOn(metaCore, rdfCommon)`.
3. Compile both test trees.

## Verification

- `sbt rdfStore/Test/compile Test/compile` green.
- `sbt metaCore/test rdfCommon/test rdfStore/Test/compile` green.
