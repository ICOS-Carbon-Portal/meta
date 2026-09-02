# 14 — Move `CpmetaConfig.scala` to `rdfCommon` unchanged

**Phase:** 4 — configuration
**Depends on:** 01
**Blocks:** 15, 16

## Goal

Relocate the configuration module and its classpath defaults into `rdfCommon` with **no
content changes**. Splitting it is task 15; doing both at once produces an unreviewable diff.

## Why

`rdfstore/src/main/scala/CpmetaConfig.scala` (301 lines) holds the entire configuration surface
of both applications, and both read it:

- **`meta`** — `Main.scala:23` (`ConfigLoader.default`), `MetaDb.scala`, `routes/MainRoute.scala`,
  `metaflow/MetaFlow.scala`, `services/upload/UploadService.scala`,
  `services/upload/validation/UploadValidator.scala`, `services/linkeddata/UriSerializer.scala`,
  `utils/owlapi/package.scala`
- **`rdfStore`** — `rdfstore/Main.scala:27-28` reads *both* `ConfigLoader.default` and
  `RdfStoreConfigLoader.default`; `persistence/RdfLogManager.scala:93` takes a `CpmetaConfig`;
  `services/citation/CitationProvider.scala` takes a `CpmetaConfig`

## Files to move

```
rdfstore/src/main/scala/CpmetaConfig.scala        -> rdf-common/src/main/scala/CpmetaConfig.scala
rdfstore/src/main/scala/rdfstore/AppConfig.scala  -> rdf-common/src/main/scala/AppConfig.scala
rdfstore/src/main/resources/application.conf      -> rdf-common/src/main/resources/application.conf
```

`AppConfig` moves too, because `ConfigLoader.default` and `RdfStoreConfigLoader.default` both
call `AppConfig.rootConfWithWorkingDirOverrides`. Its scaladoc already says it "assembles the
application configuration for both `meta` and the standalone `rdfStore` service" — it was
never store-specific, only store-hosted. Task 16 gives each application its own entry point on
top of it.

## Steps

1. `git mv` the three files.
2. Decide the package for `AppConfig`. It is currently `se.lu.nateko.cp.meta.rdfstore`, which
   becomes wrong once it lives in the shared module. Move it to `se.lu.nateko.cp.meta` and
   update its two callers in `CpmetaConfig.scala` plus `src/main/scala/Main.scala:10,19`.
3. `rdfCommon` needs `cpauth-core` (for `PublicAuthConfig`, `EmailConfig`), `spray-json`, and
   Typesafe Config.
4. Do **not** change any case class, any `jsonFormatN` arity, or any HOCON key.
5. Compile and run both applications against the existing root `application.conf`.

## Watch out

`src/main/scala/utils/owlapi/package.scala:25` uses `CpmetaConfig.getClass.getResourceAsStream`
as a classpath anchor for loading OWL resources. Those resources live in
`src/main/resources/owl/`, i.e. in `meta`, while `CpmetaConfig` is moving to a different
module. Verify the OWL ontologies still load — if the path is relative rather than absolute,
this breaks silently at runtime rather than at compile time. Consider re-anchoring it on a
class that actually lives in `meta`.

## Verification

- `sbt compile Test/compile` green.
- `sbt rdfStore/reStart` and `sbt reStart` both start and read the same root
  `application.conf` as before.
- `example.application.conf` still describes a valid configuration; no key paths changed.
