# 21 — Delete `dependsOn(rdfStore)` from `meta`

**Phase:** 6 — cut the edge and ship two applications
**Depends on:** 02–19
**Blocks:** 22

## Goal

The point of the whole plan: remove the build-time dependency of `meta` on `rdfStore`.

## The change

`build.sbt:110`:

```scala
lazy val meta = (project in file("."))
        .dependsOn(metaCore, rdfStore, metaCore % "test->test")
```

becomes:

```scala
lazy val meta = (project in file("."))
        .dependsOn(metaCore, rdfCommon, metaCore % "test->test")
```

plus any `rdfCommon % "test->test"` needed after task 18.

## Expectation

If tasks 02–19 are complete, this compiles first time. If it does not, the compile errors are
a precise list of what was missed — work through them rather than adding the dependency back.

For each error, ask **"is this storage implementation or shared domain code?"**:

- Shared domain code -> move it to `rdfCommon`.
- Storage implementation that `meta` should not touch -> `meta` is doing something it should be
  doing over HTTP instead. That is a design bug, not a packaging problem. Fix it properly.

Do not resolve errors by moving `rdfStore` internals — the Sail, the indexes, the RDF log, the
LMDB lifecycle — into `rdfCommon`. `rdfCommon`'s excluded dependency list from task 01
(no `sail-lmdb`, `sail-nativerdf`, `sail-memory`, `lwjgl`, `postgresql`, `kryo`) exists to make
that mistake fail at the classpath level rather than in review.

## Also clean up

- Remove from `meta`'s `libraryDependencies` (`build.sbt:122-157`) anything now only needed by
  `rdfStore`: `rdf4j-sail-nativerdf`, `rdf4j-sail-lmdb`, `lwjgl`, `lwjgl-lmdb`, `kryo`. Check
  each one against `meta`'s remaining sources before deleting. `rdf4j-sail-memory` may still be
  needed by `meta`'s tests.
- Check whether `tools` (`build.sbt`, `.dependsOn(meta)`) needs an explicit `rdfStore` or
  `rdfCommon` dependency now that it no longer gets one transitively.

## Verification

- `sbt clean compile Test/compile` green for every module.
- `sbt test` green for every module.
- `sbt uploadgui/fullOptJS` and `sbt assembly` both succeed.
- `sbt "show meta/dependencyClasspath"` contains no `meta-rdf-store` entry.
- The remote integration test from task 19 passes.
- Both applications start and serve traffic together against a real store.
