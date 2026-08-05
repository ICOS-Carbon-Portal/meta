# 07 — Extract `TcMetaSource`'s two shared constants

**Phase:** 2 — break the metaflow/vocabulary knot
**Depends on:** 01
**Blocks:** 09

## Goal

Remove the only dependency that `rdfStore`'s read-side code has on the metaflow model, so the
metaflow model can move to `meta` in task 09.

## Why

`rdfstore/src/main/scala/services/upload/CpmetaFetcher.scala` imports the entire metaflow
package for two string constants:

```scala
// line 10
import se.lu.nateko.cp.meta.metaflow.TcMetaSource

// lines 351-352
val modelValid = getOptionalString(instr, metaVocab.hasModel)
    .map(model => model.filter(_ != TcMetaSource.defaultInstrModel))
val serialNumberValid = getOptionalString(instr, metaVocab.hasSerialNumber)
    .map(serialNumber => serialNumber.filter(_ != TcMetaSource.defaultSerialNum))
```

These are sentinel values written by the metadata flows and filtered out on read. They are
shared vocabulary, not flow logic. Apart from `CpVocab` (task 08), this is the *only* reference
to `metaflow` from non-metaflow code in `rdfStore` — which is what makes moving the whole
metaflow model to `meta` realistic.

## Steps

1. Move `defaultInstrModel` and `defaultSerialNum` from the `TcMetaSource` companion object in
   `rdfstore/src/main/scala/metaflow/TcMetadata.scala` to `CpmetaVocab` (or a small
   `InstrumentDefaults` object alongside it — `CpmetaVocab` heads to `rdfCommon` in task 10 and
   is the natural home for shared sentinel strings).
2. In `TcMetaSource`, either re-export them (`export`/`val` alias) or update the metaflow
   sources that reference them. Prefer updating the references — an alias keeps the coupling
   alive in a less visible form.
3. Update `CpmetaFetcher.scala:10,351-352` to use the new location and drop the metaflow import.
4. Compile.

## Verification

- `grep -n 'metaflow' rdfstore/src/main/scala/services/upload/CpmetaFetcher.scala` returns
  nothing.
- `grep -rln 'metaflow' rdfstore/src/main/scala | grep -v '/metaflow/'` returns only
  `services/CpVocab.scala` (addressed in task 08).
- `sbt compile Test/compile` green.

## Risk

Low, but the constants are semantically load-bearing: they must keep the exact same string
values, since they are compared against data already in the store. Do not "tidy" them while
moving.
