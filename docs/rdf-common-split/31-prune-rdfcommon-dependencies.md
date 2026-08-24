# 31 — Prune `rdfCommon`'s unused library dependencies

**Phase:** 7 — tighten the boundary
**Depends on:** 28

## Goal

Remove library dependencies from `rdfCommon` (`build.sbt:110-133`) that its sources do not use.

## Why

`rdfCommon`'s dependency list was seeded by copying `rdfStore`'s when the module was created
(task 01) and has not been revisited as code moved in and out. Every unused entry is a library
that both applications inherit transitively, and an invitation to start using it in shared code
where it does not belong.

| Dependency | evidence |
|---|---|
| `cpauth-core` | zero references in `rdf-common/src` since the config types moved out; `metaCore` depends on it anyway, so it stays available where genuinely needed |
| `rdf4j-repository-sparql` | `SPARQLRepository` is meta's remote-store client (`MetaDb.scala`); rdf-common never names it |
| `rdf4j-queryresultio-sparqljson` | no `QueryResultIO`/`TupleQueryResultWriter` use in rdf-common |
| `rdf4j-queryresultio-text` | same |

Also assess, but do not force:

- `akka-http-spray-json` — rdf-common uses `akka.http.scaladsl.model.Uri` and `spray-json`
  separately, never the marshalling integration. `akka-http-core` would be the honest
  declaration.
- `akka-stream` — main sources use only `akka.Done` and `akka.actor.Scheduler`; after task 28
  the test tree uses no streams either. It arrives transitively via akka-http regardless, so
  removing the explicit entry is cosmetic and may be more fragile than it is worth.

Keep `rdf4j-sail-memory`: `utils/rdf4j/Loading.scala` constructs a `MemoryStore` in *main*, not
just in tests, and the existing comment on that line is correct.

Keep `rdf4j-rio-rdfxml`: `Loading.fromResource` defaults to `RDFFormat.RDFXML` and needs the
parser on the runtime classpath even though only `rio.RDFFormat` is imported.

## Steps

1. Remove the four clearly-unused entries.
2. Full clean compile of every module — a missing runtime-only artifact will not show up in an
   incremental build.

## Outcome

Removed the four unused entries. The akka declarations were also made honest:
`akka-http-spray-json` -> `akka-http-core`, since rdf-common uses `akka.http.scaladsl.model.Uri`
and never the marshalling integration. `akka-stream` stays: it is what supplies `akka.Done` and
`akka.actor.Scheduler` transitively, and akka-http-core needs it anyway.

`rdfCommon/Runtime/dependencyClasspath` after the change still contains `rdf4j-rio-rdfxml`
(declared) and, transitively, `rdf4j-repository-sparql` and `cpauth-core` -- removing an explicit
declaration of something another module legitimately pulls in is the point, not removing it from
the classpath.

## Verification

- `sbt clean` then `metaCore/test rdfCommon/test rdfStore/Test/compile Test/compile
  tools/Compile/compile` green.
- `sbt "show rdfCommon/Compile/dependencyClasspath"` still contains the rdf4j artifacts that
  `Loading` needs at runtime.
