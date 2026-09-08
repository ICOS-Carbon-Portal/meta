# 06 — Un-leak the JTS geo helpers

**Phase:** 1 — stand up `rdfCommon`
**Depends on:** 01

## Goal

Extract `JtsGeoFactory` and `ConcaveHullLengthRatio` out of the store's geo-index
implementation into a neutral home, so `meta` stops importing from
`se.lu.nateko.cp.meta.services.sparql.magic`.

## Why

This is the one place where `meta` reaches directly into `rdfStore`'s query-index internals:

- `src/main/scala/services/upload/geocov/GeoCluster.scala:9`
  — `import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory`
- `src/main/scala/services/upload/geocov/GeoCovMerger.scala:10`
  — `import se.lu.nateko.cp.meta.services.sparql.magic.{ConcaveHullLengthRatio, JtsGeoFactory}`

Both symbols are declared in `rdfstore/src/main/scala/services/sparql/magic/GeoIndex.scala`,
a file that is otherwise pure index implementation. What `meta` actually needs is a shared JTS
`GeometryFactory` and one tuning constant — nothing about indexing.

Leaving this in place would mean `meta` keeps a compile dependency on the store's query
optimizer for the sake of two declarations.

## Steps

1. Create `rdf-common/src/main/scala/utils/geo/package.scala` (or `JtsGeo.scala`) in package
   `se.lu.nateko.cp.meta.utils.geo`.
2. Move the `JtsGeoFactory` and `ConcaveHullLengthRatio` declarations there, verbatim. Both must
   remain a single shared instance/value — `GeometryFactory` carries the precision model and
   SRID, so duplicating it per module would risk subtly different geometry semantics between
   the index and the upload-side coverage merger.
3. Update the imports in `GeoCluster.scala` and `GeoCovMerger.scala`.
4. Update `GeoIndex.scala`, `GeoIndexProvider.scala`, `GeoEventProducer.scala` and
   `GeoLookup.scala` in `rdfStore` to import from the new location.
5. Ensure `rdfCommon` declares `jts-core` and `jts-io-common` (version `1.19.0`, matching both
   applications).
6. Compile.

## Verification

- `grep -rn 'sparql.magic' src/main/scala` returns nothing.
- `sbt compile Test/compile` green.
- `src/test/scala/test/services/upload/geocov/GeoCovMergerTests.scala` passes unchanged.
- `src/test/scala/test/services/sparql/index/GeoIndexTest.scala` passes unchanged (it moves to
  `rdfStore` in task 17).

## Note on the remaining `sparql.index` references

`src/test/scala/test/services/sparql/{index,magic}/**` also imports
`se.lu.nateko.cp.meta.services.sparql.index.*`, but those are *tests of `rdfStore` internals*
that happen to live in `meta`'s test tree. They are relocated in task 17, not fixed here.
