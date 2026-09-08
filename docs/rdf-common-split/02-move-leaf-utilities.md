# 02 — Move leaf utilities to `rdfCommon`

**Phase:** 1 — stand up `rdfCommon`
**Depends on:** 01
**Blocks:** 04, 18

## Goal

Relocate the general-purpose utility code that both applications use. Pure file moves —
package declarations are unchanged, so no import statement anywhere needs editing.

## Why

`meta` imports these constantly: `utils.rdf4j.*` appears in 21 files, `utils.Validated` in 11,
`utils.*` in 7, plus scattered uses of `urlEncode`, `formatBytes`, `slidingByKey`,
`getStackTrace`, `transformEither`, `printAsJsonArray`, `throttle` and `error`. None of it has
anything to do with owning a triple store.

## Files to move

From `rdfstore/src/main/scala/` to `rdf-common/src/main/scala/`, preserving relative paths:

```
utils/Mergeable.scala
utils/Validated.scala
utils/json.scala
utils/package.scala
utils/async/CancellableAction.scala
utils/async/ReadWriteLocking.scala
utils/async/package.scala
utils/rdf4j/Loading.scala
utils/rdf4j/Rdf4jIterationIterator.scala
utils/rdf4j/Rdf4jStatement.scala
utils/rdf4j/package.scala
utils/akkahttp/package.scala
api/CloseableIterator.scala
```

`utils/streams/ZipEntryFlow.scala` and `utils/owlapi/package.scala` already live in
`src/main/scala/utils/` and are `meta`-only — leave them alone.

## Steps

1. `git mv` each file. Do not edit the `package` lines; the package namespace
   `se.lu.nateko.cp.meta.utils.*` is shared across modules by design and already spans
   `rdfstore/` and `src/`.
2. Add to `rdfCommon`'s `libraryDependencies` whatever the moved files need — at minimum
   `rdf4j-rio-rdfxml` (for `Loading.scala`), the RDF4J model/query artifacts, and
   `akka-stream`/`akka-http` (for `utils/akkahttp` and `utils/async`).
3. Compile all modules.

## Verification

- `sbt compile Test/compile` green across `metaCore`, `rdfCommon`, `rdfStore`, `meta`,
  `uploadgui`, `tools`.
- `git diff --stat` shows renames only, no content changes.

## Risks

Low. The only trap is a moved file quietly needing a dependency that only `rdfStore` declares —
if that happens, check whether the file is genuinely shared before widening `rdfCommon`'s
dependency set (see task 01).
