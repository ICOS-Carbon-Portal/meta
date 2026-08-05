# 08 — Extract TC-scoped URI minting out of `CpVocab`

**Phase:** 2 — break the metaflow/vocabulary knot
**Depends on:** 01
**Blocks:** 09, 10

## Goal

Split the metaflow-aware members out of `CpVocab` into a new `TcVocab` that lives in `meta`,
leaving `CpVocab` itself free of any metaflow reference.

## Why

`CpVocab` is core shared vocabulary — 22 uses in `src/main`, and `rdfStore` needs it too — but
it currently imports the metaflow model:

```scala
// rdfstore/src/main/scala/services/CpVocab.scala:11-12
import se.lu.nateko.cp.meta.metaflow.icos.{ETC, EtcConf}
import se.lu.nateko.cp.meta.metaflow.{Role, TC, TcConf, TcId}
```

So moving `CpVocab` to `rdfCommon` would drag the whole metaflow domain model with it, even
though metadata flows are exclusively a `meta` concern.

The good news: **every one of the affected members is used only by `meta`.** Verified callers:

| Member | `CpVocab.scala` | Callers |
|---|---|---|
| `getMembership(orgId, role, lastName)` | 44-45 | `metaflow/RdfMaker.scala`, `metaflow/icos/EtcMetaSource.scala`, `ingestion/PeopleAndOrgsIngester.scala` |
| `getRole(role)` | 49 | `metaflow/RdfMaker.scala`, `metaflow/RdfReader.scala`, `ingestion/PeopleAndOrgsIngester.scala` |
| `IcosRole` extractor | 136-140 | none found — confirm and delete |
| `etcStationUriId` | 160 | `metaflow/icos/EtcMetaSource.scala:488` |
| `getEtcInstrTcId` | 161 | `metaflow/icos/EtcMetaSource.scala:563` |
| `instrCpId` | 57, 162 | `metaflow/TcMetadata.scala` (moves to `meta` in task 09) |

No `rdfStore` code outside `CpVocab.scala` itself calls any of them.

## Steps

1. Create `src/main/scala/metaflow/TcVocab.scala` in package
   `se.lu.nateko.cp.meta.metaflow` (or `...services` if you prefer to keep the vocabulary
   namespace — either works, but pick one and be consistent).
2. Move `getMembership(orgId, role, lastName)`, `getRole`, `etcStationUriId`,
   `getEtcInstrTcId` and `instrCpId` into it. Keep the plain
   `getMembership(membId: UriId)` overload (line 43) in `CpVocab` — it takes no `Role`.
3. Check line 57's internal use of `instrCpId(getEtcInstrTcId(...))(EtcConf)` — whichever
   member contains it moves to `TcVocab` too, or is rewritten to call across.
4. Confirm `IcosRole` (lines 136-140) really is dead. If so delete it; if not, it moves to
   `TcVocab`.
5. `RolesPrefix` (line 113) is a plain string used by both the extractor and `getRole` —
   duplicate it or expose it from `CpVocab`; do not let `TcVocab` reach back into private state.
6. Update the callers listed above. `TcVocab` will need a `CpVocab` instance (constructor
   parameter is simplest, since `CpVocab` is instantiated per `Envri` config).
7. Delete lines 11-12 of `CpVocab.scala`.
8. Compile.

## Verification

- `grep -n 'metaflow' rdfstore/src/main/scala/services/CpVocab.scala` returns nothing.
- `sbt compile Test/compile` green.
- `src/test/scala/test/services/CpVocabTests.scala` passes; move any assertions about the
  relocated members into a new test alongside `TcVocab`.

## Critical constraint

These functions mint **persistent URIs that already exist in the production store**. Every
generated string must be byte-identical after the move. `CpVocabTests` is the guard — if it
does not currently cover the relocated members, extend it *before* the move so you have a
baseline.
