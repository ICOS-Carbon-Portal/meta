# 11 — Move the citation stack to `rdfCommon`

**Phase:** 3 — citation and object readers
**Depends on:** 04, 10
**Blocks:** 12

## Goal

Move the citation and DOI-client code into `rdfCommon`, and record the decision that citations
are **shared infrastructure**, not `meta`-owned.

## Why this is shared, not `meta`-owned

The ownership table in `rdf-store-split.md:96` assigns "DOI, citation, landing-page
composition" to `meta`. That is right for landing pages, but wrong for citations, and the code
already shows why: `rdfStore`'s custom Sail enriches statements with citation strings at query
time.

`rdfStore` users of the citation stack:

- `services/sparql/magic/StatementsEnricher.scala`
- `services/sparql/magic/CpNotifyingSail.scala`
- `services/upload/StaticObjectFetcher.scala`
- `services/upload/DobjMetaReader.scala`
- `rdfstore/Main.scala`

`meta` users: `MetaDb.scala:21-22`, `services/linkeddata/UriSerializer.scala`,
`services/metaexport/{DataCite, SchemaOrg}.scala`, `routes/DoiRoute.scala`,
`routes/StaticRoute.scala`, `api/OrganizationExtra.scala`,
`services/upload/StatementsProducer.scala`, `services/upload/DataObjectInstanceServers.scala`.

The alternative — `rdfStore` calling back into `meta` over HTTP to resolve citations during
query evaluation — would put a synchronous network hop inside the hot path and create a runtime
cycle between two services that the whole split exists to decouple. Reject it.

## Files to move

From `rdfstore/src/main/scala/` to `rdf-common/src/main/scala/`:

```
services/citation/CitationClient.scala        (incl. PlainDoiCiter, CitationCache, DoiCache)
services/citation/CitationMaker.scala
services/citation/CitationProvider.scala
services/citation/AttributionProvider.scala
services/citation/StructuredCitations.scala
services/upload/DoiClientFactory.scala        (used by CitationClient)
```

## Steps

1. `git mv` the files; packages unchanged.
2. Add `doi-core` (`se.lu.nateko.cp %% doi-core % 0.4.5`, matching both applications) and the
   akka-http client dependencies to `rdfCommon`.
3. `CitationConfig` and `DoiConfig` live in `CpmetaConfig.scala`; they arrive in `rdfCommon` in
   task 14. Until then the retained `meta -> rdfStore` edge covers it.
4. Note the on-disk caches: `citationsCacheDump.json` and `doiMetaCacheDump.json` sit in the
   repository root. Check which process writes them and make sure the move does not change the
   path or the working directory assumption.
5. Compile.

## Verification

- `sbt compile Test/compile` green.
- Citation warm-up behaviour on startup is unchanged (`eagerWarmUp` in `CitationConfig`).
- A landing page rendered by `meta` and a `SELECT` returning `cpmeta:hasCitationString` from
  `rdfStore` produce the same citation text as before.

## Follow-up

Task 23 updates the ownership table in `rdf-store-split.md` to reflect this decision. Do not
leave the table contradicting the code.
