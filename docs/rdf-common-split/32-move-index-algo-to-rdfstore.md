# 32 — Move `core/algo` into `rdfStore`

**Phase:** 7 — tighten the boundary
**Depends on:** —

## Goal

Move `core/src/main/scala/algo/` (`HierarchicalBitmap`, `DatetimeHierarchicalBitmap`,
`BitmapExtension`) and its test into `rdfStore`.

## Why

These are the data structures behind rdfStore's SPARQL magic index. Nothing outside
`rdfstore/src/main/scala/services/sparql/{index,magic}` references them: not `meta`, not
`rdfCommon`, not the rest of `metaCore`. They are in `metaCore` because that is where the index
code originally lived, not because anything shared needs them.

Moving them makes the SPARQL index self-contained in the service that owns it, and takes
`RoaringBitmap` off `metaCore`'s dependency list.

## Risk — read before starting

`metaCore` is **published to Nexus** as `meta-core` (`build.sbt:19-70`, `publishTo`, currently
0.7.25) and is consumed by projects outside this repository. Removing public types from a
published library is a source-breaking change for any external consumer that imports them, and
this repository cannot prove none does.

`HierarchicalBitmap` is a generic, reusable structure, so external use is plausible even though
it is unlikely — it is not domain metadata like the rest of `metaCore`.

Mitigation options, in order of preference:

1. Confirm with the team that no external consumer imports `se.lu.nateko.cp.meta.core.algo`, then
   move and bump `metaCore`'s minor version.
2. Move, and leave deprecated type aliases in `metaCore` for one release.
3. Defer.

This task file assumes option 1 has been agreed. If it has not, stop and raise it — the code
change is easy, the compatibility decision is not.

## Steps

1. `git mv core/src/main/scala/algo rdfstore/src/main/scala/algo` (keep the package name
   `se.lu.nateko.cp.meta.core.algo` so no import site changes, or rename it deliberately —
   decide, do not drift).
2. Move `core/src/test/scala/algo/DatetimeHierarchicalBitmapTests.scala` alongside.
3. Move the `RoaringBitmap` dependency from `metaCore` to `rdfStore`.
4. Bump `metaCore`'s version.

## Outcome

Done on the user's instruction after the published-library risk above was raised and
reaffirmed. **Option 1 was not actually confirmed with the team** -- no one has checked whether an
external consumer imports `se.lu.nateko.cp.meta.core.algo`. If one does, this is a source-breaking
change for it, and the mitigation is deprecated type aliases in `metaCore` (option 2) or a revert.
`metaCore` is bumped 0.7.25 -> 0.8.0 to signal it.

The package name stays `se.lu.nateko.cp.meta.core.algo` even though the sources now live in
rdfStore. This was the deliberate choice the task asked for, and *not* only to avoid churn in 14
import sites: `HierarchicalBitmap` is `java.io.Serializable` and participates in the kryo index
dump that rdfStore restores from disk at boot (`IndexHandler`), so a package rename risks
invalidating every existing dump. A comment on the file records this.

`RoaringBitmap` moved from `metaCore`'s dependencies to `rdfStore`'s.

## Verification

- `sbt metaCore/test rdfStore/test` green.
- `grep -rn "core.algo" src/ core/ rdf-common/` returns nothing.
