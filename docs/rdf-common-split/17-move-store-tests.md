# 17 — Move store-owned tests out of `src/test`

**Phase:** 5 — tests
**Depends on:** 04, 06, 10, 11, 12
**Blocks:** 21

## Goal

Relocate the tests that exercise `rdfStore` internals but currently live in `meta`'s test tree.

## Why

`rdfstore/src/test` contains only four files
(`persistence/LoggingInstanceServerTest`, `persistence/RdfUpdateLogIngesterTest`,
`rdfstore/RouteTest`, `rdfstore/SparqlFailureHandlerTest`), while 16 files under
`src/test/scala/test/services/sparql/` test the custom index, the query-fusion optimizer and
the geo index — all of which are `rdfStore` code. As long as they sit in `meta`, `meta`'s test
configuration needs `rdfStore` on the classpath, and task 21 cannot succeed.

## Files to move to `rdfstore/src/test/scala/`

Index and index-serialization:

```
test/services/sparql/index/GeoIndexTest.scala
test/services/sparql/index/IndexDataTest.scala
test/services/sparql/index/LargeScaleDatetimeTest.scala
test/services/sparql/index/SamplingHeightHierarchicalBitmapTests.scala
test/services/sparql/index/SerializationTests.scala
test/services/sparql/index/StringHierarchicalBitmapTests.scala
```

Query-fusion optimizer:

```
test/services/sparql/magic/fusion/DofPatternFusionTests.scala
test/services/sparql/magic/fusion/EarlyDobjInitSearch.scala
test/services/sparql/magic/fusion/PatternFinder.scala
test/services/sparql/magic/fusion/StatementPatternSearch.scala
test/services/sparql/magic/fusion/StatsFetchPatternSearchTests.scala
test/services/sparql/magic/fusion/TestQueries.scala
```

SPARQL behaviour and regression corpus:

```
test/services/sparql/SparqlTests.scala
test/services/sparql/regression/QueryTests.scala
test/services/sparql/regression/TestDb.scala
test/services/sparql/regression/TestQueries.scala
```

Plus the fixtures they read from `src/test/resources/` — check `sparql/`, `rdf/` and `owl/`.

## The complication: `TestDb` depends on `meta`

`src/test/scala/test/services/sparql/regression/TestDb.scala` imports:

```scala
import se.lu.nateko.cp.meta.ingestion.{BnodeStabilizers, Ingestion, RdfXmlFileIngester}  // meta
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner                                   // meta
```

It seeds an LMDB store from RDF/XML fixtures using `meta`'s ingestion code, then queries it
through `meta`'s SPARQL runner. Moving it to `rdfStore` as-is would recreate the dependency in
the opposite direction — worse than the one being removed.

Pick one, in order of preference:

1. **Replace the seeding.** The regression corpus only needs the fixtures loaded into named
   graphs. Use plain RDF4J `RepositoryConnection.add(reader, baseUri, RDFFormat.RDFXML, ctx)`
   instead of `RdfXmlFileIngester` + `Ingestion`. Loses the b-node stabilisation that
   `BnodeStabilizers` provides, so check whether any regression query depends on stable blank
   node identity.
2. **Move `Rdf4jSparqlRunner` to `rdfCommon`.** It lives in `src/main/scala/services/` and
   implements the `SparqlRunner` trait that task 04 moved. That is a defensible move
   independently — but it only solves half the problem, since the ingestion dependency remains.
3. **Leave the regression suite in `meta`** and have it drive a running `rdfStore` over HTTP.
   This is really task 19 wearing a different hat, and is the better long-term shape: it tests
   the deployed topology rather than an embedded store. Costs more to build.

Decide before starting; do not discover this halfway through the move.

## Steps

1. Resolve the `TestDb` question above.
2. `git mv` the files, adjusting the `test` package prefix if you want `rdfStore`'s test tree to
   match its own convention (its existing tests use `se.lu.nateko.cp.meta.persistence` and
   `...meta.rdfstore` without a `test` segment — pick one convention for the module).
3. Move the fixtures and update any resource paths.
4. Add the test dependencies `rdfStore` now needs: `scalatest`, `akka-http-testkit`,
   `akka-testkit` are already declared (`build.sbt:292-294`); add `commons-io` if `TestDb`
   comes across, and `scalacheck` if any moved test uses it.

## Verification

- `sbt rdfStore/test` runs the moved suites and they pass.
- `sbt meta/test` still passes with the suites gone.
- `find src/test/scala/test/services/sparql -name '*.scala'` returns nothing (or only what you
  deliberately kept per option 3).
