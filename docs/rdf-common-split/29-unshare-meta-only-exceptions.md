# 29 — Move the meta-only exceptions out of `rdfCommon`

**Phase:** 7 — tighten the boundary
**Depends on:** 03

## Goal

Reduce `rdf-common/src/main/scala/services/Exceptions.scala` to what both applications actually
use, and move the rest into `meta`.

## Why

Task 03 moved the whole hierarchy into `rdfCommon` on the strength of "`rdfStore` also raises
some of them". That is no longer true — after the citation/derived-metadata narrowing (tasks 24
and 25), `rdfStore` references exactly one member, `MetadataException`, at
`rdfstore/src/main/scala/services/sparql/magic/Filtering.scala:91`, where it is used as a generic
"geo index not ready" runtime error.

Current usage:

| Exception | rdfStore | meta | verdict |
|---|---|---|---|
| `MetadataException` | 1 | 13 | shared — keep in `rdfCommon` |
| `ServiceException` (sealed base) | 0 | 1 | base of the above — keep |
| `UploadUserErrorException` | 0 | 4 | meta |
| `UnauthorizedUploadException` | 0 | 3 | meta |
| `UnauthorizedStationUpdateException` | 0 | 3 | meta |
| `UnauthorizedUserInfoUpdateException` | 0 | 2 | meta |
| `IllegalLabelingStatusException` | 0 | 2 | meta |
| `UploadCompletionException` | 0 | 0 | dead — delete |
| `PidMintingException` | 0 | 0 | dead — delete |
| `CacheSizeLimitExceeded` | 0 | 0 | dead — delete |

Task 03's own closing section anticipated exactly this ("If a later cleanup wants the store to
stop knowing about `UnauthorizedUploadException`, the file can be split").

## Design note

`ServiceException` is `sealed`, so subclasses must live in the same file. Un-seal it (it is
already extended across two modules' worth of intent, and nothing pattern-matches exhaustively on
it — verify with a grep for `case _: ServiceException` and matches on the base type before
changing it).

Keep meta's new file at `src/main/scala/services/Exceptions.scala`, in the same
`se.lu.nateko.cp.meta.services` package, so no import sites change. Do not merge it with the
pre-existing, unrelated `src/main/scala/ingestion/Exceptions.scala`.

## Steps

1. Un-seal `ServiceException` in rdf-common; leave it and `MetadataException` there.
2. Delete the three unused exceptions.
3. Create `src/main/scala/services/Exceptions.scala` with the five meta-only subclasses.
4. Compile; no import changes should be needed (same package).

## Verification

- `sbt rdfCommon/Test/compile rdfStore/Test/compile Test/compile` green.
- `grep -rn "ServiceException" rdfstore/src` returns only what `MetadataException` needs.
