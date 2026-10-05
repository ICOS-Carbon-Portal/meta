# 10 — Move the vocabularies to `rdfCommon`

**Phase:** 2 — break the metaflow/vocabulary knot
**Depends on:** 08, 09
**Blocks:** 11, 12, 18

## Goal

Move the shared RDF vocabularies and URI-identifier types into `rdfCommon`.

## Why

Both applications mint and match Carbon Portal URIs. `CpVocab` has 22 uses in `src/main`,
`CpmetaVocab` 13, `UriId` 12, and `rdfStore`'s index, enricher and fetchers depend on all of
them. After task 08 these files have no metaflow reference left, so they can move cleanly.

## Files to move

From `rdfstore/src/main/scala/` to `rdf-common/src/main/scala/`:

```
api/CustomVocab.scala        (CustomVocab, UriId)
services/CpVocab.scala
services/CpmetaVocab.scala
OntoConstants.scala
```

## `uploadgui` needs repointing

`build.sbt:250` compiles `OntoConstants.scala` directly into the Scala.js frontend via
`Compile / unmanagedSources`:

```scala
"rdfstore/src/main/scala/OntoConstants.scala",
```

Change this to `"rdf-common/src/main/scala/OntoConstants.scala"`. Missing this breaks the
frontend build, and because it is an `unmanagedSources` path rather than an import, the Scala
compiler will not flag it — you get a "not found: OntoConstants" failure inside `uploadgui`
instead.

## Steps

1. Verify preconditions: `grep -n 'metaflow' rdfstore/src/main/scala/services/CpVocab.scala`
   returns nothing (task 08), and `rdfstore/src/main/scala/metaflow/` is gone (task 09).
2. `git mv` the four files; packages unchanged.
3. Update `build.sbt:250`.
4. Compile everything, **including `uploadgui`**.

## Verification

- `sbt compile Test/compile` green across all modules.
- `sbt uploadgui/fastOptJS` succeeds.
- `src/test/scala/test/services/CpVocabTests.scala` and `test/api/CustomVocabTests.scala` pass
  (they move to `rdfCommon` in task 18).
