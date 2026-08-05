# 13 — Move `MetadataUpdater` to `meta`

**Phase:** 3 — citation and object readers
**Depends on:** 04, 12

## Goal

Move `rdfstore/src/main/scala/services/upload/MetadataUpdater.scala` to
`src/main/scala/services/upload/MetadataUpdater.scala`.

## Why

It is write-side: it computes the RDF diff between an existing object's metadata and an
incoming update. Uploads are unambiguously `meta`'s concern
(`rdf-store-split.md:94` — "Upload validation and RDF statement production | `meta`").

Callers in `meta`: `services/upload/UploadService.scala`,
`services/upload/DoiService.scala`. The file declares `MetadataUpdater`,
`ObjMetadataUpdater` and `StaticCollMetadataUpdater`.

## The one thing to check first

`rdfstore/src/main/scala/instanceserver/InstanceServer.scala` contains a reference to
`MetadataUpdater`. Determine what it is:

- **A comment or scaladoc mention** — delete or reword it; nothing else to do.
- **A real code dependency** — invert it before moving. `InstanceServer` is the lower-level
  abstraction and must not know about upload-time diffing. Extract whatever it needs into
  `InstanceServer` itself, or pass it in.

Note that task 04 already moves `InstanceServer.scala` into `rdfCommon`, so if the dependency
is real it will surface as a compile error there first.

## Steps

1. Resolve the `InstanceServer.scala` reference as above.
2. `git mv` the file; package `se.lu.nateko.cp.meta.services.upload` is unchanged.
3. Delete `rdfstore/src/main/scala/services/upload/` if now empty.
4. Move its test: `src/test/scala/test/services/upload/MetadataUpdaterTests.scala` already sits
   in `meta`'s test tree and stays there.
5. Compile.

## Verification

- `sbt compile Test/compile` green.
- `MetadataUpdaterTests` passes.
- `grep -rn 'MetadataUpdater' rdfstore/ rdf-common/` returns nothing.
