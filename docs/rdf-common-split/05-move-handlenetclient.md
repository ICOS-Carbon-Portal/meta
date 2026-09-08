# 05 — Move `HandleNetClient` to `rdfCommon`

**Phase:** 1 — stand up `rdfCommon`
**Depends on:** 01, 02

## Goal

Move `rdfstore/src/main/scala/api/HandleNetClient.scala` and
`rdfstore/src/main/scala/api/HandleData.scala` to `rdf-common/src/main/scala/api/`.

## Why

Both applications use the Handle.net PID client, so neither can own it:

- **`rdfStore`** — `services/citation/CitationProvider.scala`,
  `services/upload/StaticObjectFetcher.scala`
- **`meta`** — `services/upload/completion/PidMinter.scala`,
  `services/upload/completion/{UploadCompleter, TimeSeriesUploadCompleter, NetCdfUploadCompleter}.scala`,
  `services/upload/UploadService.scala`, `services/linkeddata/UriSerializer.scala`

## Steps

1. `git mv` both files, keeping the `se.lu.nateko.cp.meta.api` package.
2. Ensure `rdfCommon` declares `akka-http`/`akka-stream` and whatever crypto artifacts the
   client needs (it does key-based authentication against Handle.net; see
   `src/test/resources/crypto/` for the test fixtures).
3. `HandleNetClientConfig` lives in `CpmetaConfig.scala` and moves in task 14. Until then it is
   still reachable through the retained `meta -> rdfStore` edge, so this task does not block on it.
4. Compile.

## Verification

- `sbt compile Test/compile` green.
- `src/test/scala/test/api/HandleNetClientTests.scala` still passes. It moves to `rdfCommon`'s
  test sources in task 18.
