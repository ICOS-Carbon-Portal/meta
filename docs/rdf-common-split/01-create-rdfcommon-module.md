# 01 — Create the `rdfCommon` sbt module

**Phase:** 1 — stand up `rdfCommon`
**Depends on:** nothing
**Blocks:** 02–18

## Goal

Add an empty fourth module that both applications depend on, so subsequent tasks have
somewhere to move code to. No code moves in this task.

## Why

`meta` currently depends on `rdfStore` (`build.sbt:110`) not because it needs the triple
store, but because `rdfStore` holds the shared configuration, vocabularies, `InstanceServer`
API, utilities and domain services. Those need a neutral home that is neither application.

`metaCore` cannot be that home: it is published to Nexus, drives the TypeScript and Python
code generators (`cpTsGenTypeMap`, `cpPyGenTypeMap` in `build.sbt:41-64`), and deliberately
carries no RDF4J dependency. Adding RDF4J there would pollute a published library that other
Carbon Portal projects consume.

## Changes

In `build.sbt`, add before the `meta` project definition:

```scala
lazy val rdfCommon = (project in file("rdf-common"))
    .dependsOn(metaCore)
    .settings(
        name := "meta-rdf-common",
        version := "0.1.0",
        scalacOptions ++= commonScalacOptions,
        libraryDependencies ++= Seq(
            // filled in as code arrives; see below
        )
    )
```

Then:

- `rdfStore`: add `.dependsOn(rdfCommon)` (`build.sbt:269`).
- `meta`: add `rdfCommon` to its `dependsOn` list, **keeping `rdfStore`** (`build.sbt:110`).

Create the source directories:

```
rdf-common/src/main/scala/
rdf-common/src/test/scala/
```

## Dependency set

Start minimal and add as tasks 02–14 move code in. The eventual set, derived from what the
moved files import:

| Dependency | Needed by |
|---|---|
| `rdf4j-repository-sail`, `rdf4j-repository-sparql` | `InstanceServer`, `Rdf4jInstanceServer` (task 04) |
| `rdf4j-rio-rdfxml` | `utils/rdf4j/Loading.scala` (task 02) |
| `rdf4j-queryresultio-sparqljson`, `-text` | `SparqlRunner` (task 04) |
| `akka-stream`, `akka-http-spray-json` | `HandleNetClient`, `CitationClient` (tasks 05, 11) |
| `jts-core`, `jts-io-common` | geo helpers (task 06) |
| `doi-core` | `CitationMaker`, `DoiClientFactory` (task 11) |

**Explicitly excluded**, so the boundary is enforced by the classpath rather than by
convention: `rdf4j-sail-lmdb`, `rdf4j-sail-nativerdf`, `rdf4j-sail-memory`, `lwjgl`,
`lwjgl-lmdb`, `postgresql`, `kryo`. These stay in `rdfStore`. If a file being moved turns out
to need one of them, that file belongs in `rdfStore`, not `rdfCommon` — treat it as a signal,
not as a reason to widen the dependency set.

OWL API stays in `meta` only.

## Verification

- `sbt compile` and `sbt Test/compile` succeed for all modules.
- `sbt "show rdfCommon/libraryDependencies"` contains no LMDB, NativeStore or PostgreSQL entry.

## Notes

The duplicated dependency blocks between `meta` and `rdfStore` in `build.sbt` will shrink as
code moves. Consider factoring the RDF4J version-pinned lists into a shared `val` once the
moves settle, but do not do it in this task — keep the diff reviewable.
