# 33 — Give `CitationProvider` a read-only connection instead of an `InstanceServer`

**Phase:** 7 — tighten the boundary
**Depends on:** 25

## Goal

Stop `rdfstore/src/main/scala/services/citation/CitationProvider.scala:119` from constructing an
`Rdf4jInstanceServer`.

## Why

`InstanceServer` is the mutable, write-capable abstraction meta uses to administer named graphs:
`applyAll`, `applyDiff`, `writeContext`, ingestion and log replay. rdfStore instantiates one for
a single read — a `getStatements` sweep to collect every `hasDoi` object at start-up — and then
uses the repository directly everywhere else.

It is rdfStore's last dependency on that model. It already uses no `TriplestoreConnection`,
`SparqlRunner`, `CustomVocab` or `UriId` directly, and task 25 removed the
`instanceServers`-shaped citation graph config for the same reason: "that name was residue from
the mutable `InstanceServer` model" (`CitationGraphsConfig.scala:26`).

This is a small change with no behavioural component. Its value is that it closes the door on
rdfStore growing write-side instance-server usage later.

## Design

`Rdf4jInstanceServer.access` is doing two things worth keeping: opening a connection and
supplying it as a `TriplestoreConnection & SparqlRunner` context parameter so `StatementSource`'s
extension methods resolve. The replacement needs the same, minus the write surface.

Check what already exists before adding anything — `Rdf4jSailConnection` /
`Rdf4jTriplestoreConnection` in `instanceserver/Rdf4jInstanceServer.scala` may be directly usable,
in which case this is an import change rather than new code. Prefer reusing them over writing a
parallel helper.

## Steps

1. Establish what the minimal read-only entry point is (existing type, or a small factory next to
   it in rdf-common).
2. Rewrite the `val server = ...` / `server.access` block in `CitationProvider`.
3. Confirm `server` is not used elsewhere in the file or exposed to callers.

## Verification

- `sbt rdfStore/test` green — the citation and derived-metadata suites cover this path.
- `grep -rnw "InstanceServer" rdfstore/src/main` returns only comments.
