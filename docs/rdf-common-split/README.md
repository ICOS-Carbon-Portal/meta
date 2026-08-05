# Extracting `rdfCommon`: task index

Companion to [`../rdf-store-split.md`](../rdf-store-split.md). That document describes the
process split, which is already implemented. This directory covers the work that remains:
removing the build-time dependency `meta -> rdfStore`.

## Problem

The process split is done — nothing under `src/` touches a Sail, LMDB, or the RDF-log
implementation. `MetaDb.scala` builds a `SPARQLRepository` and talks to `rdfStore` over HTTP.
But `build.sbt` still has `meta.dependsOn(rdfStore)`, because `rdfStore` is doubling as the
shared library for everything both applications need: configuration, vocabularies, the
`InstanceServer` API, generic utilities, domain exceptions, the metaflow model, the citation
stack and the read-side object fetchers.

This is open slice 4 in `rdf-store-split.md`.

## Target

```text
                metaCore          (no RDF4J; published to Nexus, drives TS/Py codegen)
                    ^
                rdfCommon         (RDF4J-level shared code)
                    ^  ^
        rdfStore ---'  '--- meta
```

`rdfStore` and `meta` both depend on `rdfCommon`; neither depends on the other.

## Two decisions that shape the plan

1. **`metaCore` is not the home for this code.** It is published as a library, feeds the
   TypeScript and Python code generators, and deliberately has no RDF4J on its classpath.
2. **The citation stack is shared, not `meta`-owned.** `rdfStore`'s custom Sail enriches
   statements with citations (`magic/StatementsEnricher.scala`, `magic/CpNotifyingSail.scala`),
   so it genuinely needs `CitationProvider` and the object fetchers. Having the store call back
   into `meta` over HTTP from inside query evaluation would create a runtime cycle in the hot
   path. This contradicts the ownership table in `rdf-store-split.md`, which is corrected in
   task 23.

## Working principles

- **Move first, split later.** Most tasks are pure file relocations with unchanged package
  names — no import churn, trivially reviewable diffs. Only the configuration and the
  metaflow/vocabulary coupling need real code changes; those come after the mechanical moves.
- **Keep `meta.dependsOn(rdfStore)` until task 21.** The build stays green throughout, and
  cutting the edge becomes a one-line change that should break nothing.

## Tasks

Progress: **0 / 23 complete.** Tick a box when the task's own verification section passes, and
update the count above.

### Phase 1 — stand up `rdfCommon` (mechanical)

- [ ] [01](01-create-rdfcommon-module.md) — Create the `rdfCommon` sbt module
- [ ] [02](02-move-leaf-utilities.md) — Move leaf utilities
- [ ] [03](03-move-domain-exceptions.md) — Move domain exceptions
- [ ] [04](04-move-rdf-access-api.md) — Move the RDF access API
- [ ] [05](05-move-handlenetclient.md) — Move `HandleNetClient`
- [ ] [06](06-unleak-geo-helpers.md) — Un-leak the JTS geo helpers

### Phase 2 — break the metaflow/vocabulary knot

- [ ] [07](07-extract-tcmetasource-constants.md) — Extract `TcMetaSource`'s two shared constants
- [ ] [08](08-extract-tc-vocab.md) — Extract TC-scoped URI minting out of `CpVocab`
- [ ] [09](09-move-metaflow-model.md) — Move the metaflow model to `meta`
- [ ] [10](10-move-vocabularies.md) — Move the vocabularies to `rdfCommon`

### Phase 3 — citation and object readers

- [ ] [11](11-move-citation-stack.md) — Move the citation stack to `rdfCommon`
- [ ] [12](12-move-object-fetchers.md) — Move the read-side object fetchers to `rdfCommon`
- [ ] [13](13-move-metadataupdater.md) — Move `MetadataUpdater` to `meta`

### Phase 4 — configuration

- [ ] [14](14-move-config-verbatim.md) — Move `CpmetaConfig.scala` to `rdfCommon` unchanged
- [ ] [15](15-split-config.md) — Split the configuration three ways *(optional for task 21)*
- [ ] [16](16-meta-appconfig.md) — Give `meta` its own `AppConfig`

### Phase 5 — tests

- [ ] [17](17-move-store-tests.md) — Move store-owned tests out of `src/test`
- [ ] [18](18-move-shared-tests.md) — Move shared-code tests to `rdfCommon`
- [ ] [19](19-remote-integration-test.md) — Add a remote integration test on LMDB

### Phase 6 — cut the edge and ship two applications

- [ ] [20](20-rdfstore-assembly-deploy.md) — Give `rdfStore` assembly and deploy configuration
- [ ] [21](21-remove-dependson.md) — Delete `dependsOn(rdfStore)` from `meta`
- [ ] [22](22-ci-guard.md) — Add a CI guard against the dependency returning
- [ ] [23](23-update-split-doc.md) — Update `rdf-store-split.md`

## Out of scope

Service authentication on `/logged-update` and `/admin/read-only`, and idempotent mutation
batches (slices 1 and 2 in `rdf-store-split.md`) are orthogonal to the module split and can
proceed in parallel.
