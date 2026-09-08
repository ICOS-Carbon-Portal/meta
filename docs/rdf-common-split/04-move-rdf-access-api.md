# 04 — Move the RDF access API to `rdfCommon`

**Phase:** 1 — stand up `rdfCommon`
**Depends on:** 01, 02
**Blocks:** 11, 12, 18

## Goal

Move the abstraction that `meta` programs against when it reads and writes triples, while
leaving every piece of the storage *implementation* in `rdfStore`.

## Why

This is the largest single reason `meta` links against `rdfStore`, and it is entirely
legitimate API use rather than implementation coupling: `InstanceServer` (13 uses in
`src/main`), `StatementSource` (10), `RdfUpdate` (12), `RdfLens`/`RdfLenses` (12),
`SparqlRunner` (7), `TriplestoreConnection` (7). `rdf-store-split.md:21` already observes that
this code sits on a useful abstraction boundary — it consumes RDF4J `Repository` /
`RepositoryConnection`, never LMDB classes.

## Move to `rdfCommon`

```
instanceserver/InstanceServer.scala          (incl. TriplestoreConnection)
instanceserver/StatementSource.scala
instanceserver/RdfUpdate.scala               (incl. RdfAssertion)
instanceserver/RdfMutation.scala
instanceserver/Rdf4jInstanceServer.scala
api/RdfLenses.scala                          (RdfLens, RdfLenses, DobjLens)
api/SparqlRunner.scala
```

## Move to `meta` (`src/main/scala/`)

These are client-side only — `rdfStore` does not use them:

```
instanceserver/RemoteRdf4jInstanceServer.scala
```

and the client half of `persistence/RdfHistory.scala`. Inspect that file first: if it contains
both `RdfHistoryClient` (used by `MetaDb.scala:20`) and server-side history types, split it,
sending only the client to `meta`.

## Stays in `rdfStore`

Everything that knows about the log or the local store:

```
instanceserver/LoggingInstanceServer.scala
persistence/RdfUpdateLog.scala
persistence/RdfUpdateLogIngester.scala
persistence/RdfLogManager.scala
persistence/InMemoryRdfLog.scala
persistence/postgres/*
```

`src/main/scala/instanceserver/WriteNotifyingInstanceServer.scala` is already in `meta` and
stays there.

## Steps

1. Move the files listed above.
2. `InstanceServer.scala` currently references `MetadataUpdater` — check whether this is a real
   dependency or only a comment. If real, invert it before the move; `MetadataUpdater` is
   write-side and heads to `meta` in task 13.
3. Confirm nothing moved to `rdfCommon` imports `org.eclipse.rdf4j.sail.*`. If something does,
   it is store implementation and should not move.
4. Compile.

## Verification

- `sbt compile Test/compile` green.
- `grep -rn 'org.eclipse.rdf4j.sail' rdf-common/src/main` returns nothing.
- `grep -rn 'postgres\|Lmdb\|NativeStore' rdf-common/src/main` returns nothing.

## Risks

Medium. `InstanceServer.scala` is a hub file with several inner types; expect the compiler to
surface a few unexpected inbound references. Resolve each by asking "is this caller storage
implementation or domain code?" rather than by moving more files into `rdfCommon`.
