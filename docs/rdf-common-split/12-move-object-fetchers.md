# 12 — Move the read-side object fetchers to `rdfCommon`

**Phase:** 3 — citation and object readers
**Depends on:** 04, 10, 11

## Goal

Move the code that reads static objects and collections out of RDF into `rdfCommon`, since
both the store's enrichment path and `meta`'s presentation layer need it.

## Why

These readers turn triples into `core.data` objects. `rdfStore` needs them because
`CitationProvider` and `AttributionProvider` build citations from full object metadata, and
`GeoLookup` / `GeoIndexProvider` read object metadata while building the geo index. `meta`
needs them for landing pages, DataCite export and upload validation.

Confirmed `rdfStore` callers:

- `services/citation/CitationProvider.scala`, `services/citation/AttributionProvider.scala`
- `services/sparql/magic/GeoLookup.scala`, `services/sparql/magic/GeoIndexProvider.scala`

Confirmed `meta` callers of `StaticObjectReader`, `DobjMetaReader` and `CpmetaReader`:
`MetaDb.scala:26`, `services/linkeddata/UriSerializer.scala`, `metaflow/RdfReader.scala`,
`services/upload/UploadService.scala`, `services/upload/validation/ScopedValidator.scala`,
`services/upload/completion/{TimeSeries,NetCdf}UploadCompleter.scala`.

## Files to move

From `rdfstore/src/main/scala/services/upload/` to
`rdf-common/src/main/scala/services/upload/`:

```
CpmetaFetcher.scala        (CpmetaReader)
DobjMetaReader.scala
StaticObjectFetcher.scala  (StaticObjectReader)
CollectionFetcher.scala
VarMetaLookup.scala
```

`MetadataUpdater.scala` and `DoiClientFactory.scala` are **not** in this list — see task 13 and
task 11 respectively.

## Steps

1. Verify task 07 landed: `CpmetaFetcher.scala` must no longer import `metaflow`.
2. `git mv` the five files; packages unchanged.
3. Compile.

## Verification

- `sbt compile Test/compile` green.
- `rdfstore/src/main/scala/services/upload/` contains only `MetadataUpdater.scala` at this
  point (and nothing at all after task 13 — remove the directory then).
- Landing-page rendering and `rdfStore`'s statement enrichment both still work.

## Design note

`CollectionFetcher.scala` and `VarMetaLookup.scala` have no direct `src/main` references
(`VarMetaLookup` is used three times inside `rdfStore`), but they are part of the same reader
cluster and are reached transitively. Moving them together keeps the cluster coherent; if the
compiler shows `VarMetaLookup` is genuinely store-internal, leave it in `rdfStore` instead.
