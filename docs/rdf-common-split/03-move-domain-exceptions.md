# 03 — Move domain exceptions to `rdfCommon`

**Phase:** 1 — stand up `rdfCommon`
**Depends on:** 01

## Goal

Move `rdfstore/src/main/scala/services/Exceptions.scala` to
`rdf-common/src/main/scala/services/Exceptions.scala`.

## Why

`meta` throws and catches these across the upload, labeling and routing layers:

| Exception | uses in `src/main` |
|---|---|
| `MetadataException` | 13 |
| `UploadUserErrorException` | 4 |
| `UnauthorizedUploadException` | 3 |
| `UnauthorizedStationUpdateException` | 3 |
| `UnauthorizedUserInfoUpdateException` | 2 |
| `IllegalLabelingStatusException` | 2 |
| `ServiceException` | 1 |

They are metadata-domain error types, not storage errors. `rdfStore` also raises some of them,
so they belong in the shared module rather than in `meta`.

Note that `src/main/scala/ingestion/Exceptions.scala` is a separate, `meta`-only file — do not
confuse the two or attempt to merge them.

## Steps

1. `git mv rdfstore/src/main/scala/services/Exceptions.scala rdf-common/src/main/scala/services/Exceptions.scala`
2. Leave the `package se.lu.nateko.cp.meta.services` declaration as is.
3. Compile.

## Verification

- `sbt compile Test/compile` green.
- `git diff --stat` shows a rename with no content change.

## Follow-up worth considering (not in this task)

Some of these carry HTTP status semantics that `meta`'s routing layer interprets. If a later
cleanup wants the store to stop knowing about `UnauthorizedUploadException`, the file can be
split — but that is a separate change and not required to cut the module dependency.
