# 15 — Split the configuration three ways

**Phase:** 4 — configuration
**Depends on:** 14
**Blocks:** 16

## Goal

Break `CpmetaConfig` into a shared part, a store-only part and a `meta`-only part, so neither
application carries the other's settings in its type model.

## Why

Today `CpmetaConfig` is a single 16-field case class
(`given RootJsonFormat[CpmetaConfig] = jsonFormat16(...)`) describing both applications, and
`rdfstore/Main.scala:27` loads the whole thing. That means `rdfStore` fails to start if a
`meta`-only section such as `stationLabelingService` or `onto` is malformed, and vice versa.

**This is the only task in the plan that is optional for cutting the module dependency.**
Task 21 works with the config left whole in `rdfCommon`. Do this one for the operational
benefit — independent configuration validation and smaller blast radius — not because task 21
needs it.

## Proposed classification

**Shared (`rdfCommon`)** — both applications genuinely read these:

| Type | Why shared |
|---|---|
| `CitationConfig`, `DoiConfig`, `DoiMemberConfig` | citation stack lives in `rdfCommon` (task 11) |
| `HandleNetClientConfig` | `HandleNetClient` lives in `rdfCommon` (task 05) |
| `DataObjectInstServersConfig`, `DataObjectInstServerDefinition` | Both applications use the data-object graph shape, but each owns its own containing `instanceServers` configuration |
| `UploadServiceConfig` | also needed by `CitationProvider.scala:22` |
| `SubmittersConfig`, `DataSubmitterConfig` | referenced from the shared upload readers |
| `MetaCoreConfig`, `PublicAuthConfig` | already from `metaCore` / `cpauth-core` |
| `RestheartConfig` | shared client config |

**Store-only (`rdfStore`)**:
`RdfStorageConfig`, `LmdbConfig`, `RdfStoreConfig`, `RdflogConfig`, `DbServer`,
`DbCredentials`, `SparqlServerConfig`, and the `rdfStore { httpBindInterface, port }` listener
settings. `RdfStoreConfigLoader` moves with them.

**`meta`-only (`src/main`)**:
`LabelingServiceConfig`, `OntoConfig`, `SchemaOntologyConfig`, `InstOntoServerConfig`,
`MetaFlowConfig` + `IcosMetaFlowConfig` + `CitiesMetaFlowConfig` + `MetaUploadConf`,
`EtcConfig`, `StatsClientConfig`, `SentryConfig`, `RemoteRdfRepositoryConfig`,
`fileStoragePath`, and `meta`'s own `port` / `httpBindInterface`.

`InstanceServersConfig`, `InstanceServerConfig`, and `IngestionConfig` are meta-only. rdfStore's
`CitationProvider` instead reads `StoreInstanceServersConfig`, a minimal independent view containing
only the graph contexts, data-object graph definitions, and `cpMetaInstanceServerId` it needs.
`UploadServiceConfig` remains split into the full meta type and rdfStore's narrow upload-target view.

## Hard constraint: HOCON keys must not change

`rdf-store-split.md:156` commits to deployment compatibility. Existing overrides use
`cpmeta.rdfStorage`, `cpmeta.rdfLog`, per-instance `logName`, `skipLogIngestionAtStart`,
`logIngestionFromId`, per-format `replayLogFrom`, and the `rdfStore.*` defaults. Every one of
those paths must keep resolving to the same value after the split. The split is in the Scala
type model and the JSON formats, **not** in the configuration file layout.

One documented exception, from the later audit of `rdf-common`'s `reference.conf` (see the
follow-up note in [README.md](README.md)): `cpmeta.rdfStorage` was dropped rather than kept
resolving. It is dead — `CpmetaConfig` has no `rdfStorage` field and `rdfStore` reads
`rdfStore.rdfStorage` — so an existing override of it neither changed behaviour before nor
does now. Which reference.conf a still-live key is *defined* in is likewise not part of the
contract; the effective merged tree per application is what must not change, and was diffed
key by key (before/after, with and without a working-dir `application.conf`) when keys moved.

## Steps

1. Split `CpmetaConfig.scala` into three files in their respective modules, keeping every case
   class definition byte-identical.
2. Split `ConfigLoader` likewise: shared `given` instances in `rdfCommon`, the rest alongside
   their case classes. Each application gets a loader that parses its own view of the `cpmeta`
   section — Typesafe Config ignores unknown fields at the spray-json layer only if the formats
   are written to, so check whether `parseAs` is strict about extra keys before assuming a
   partial parse works.
3. Update `rdfstore/Main.scala:27-28` to load only the store's view.
4. Update `src/main/scala/Main.scala:23` to load only `meta`'s view.
5. `persistence/RdfLogManager.scala:93` takes `(RdfStoreConfig, CpmetaConfig)` — narrow the
   second parameter to just the shared instance-server configuration it actually uses.
6. `CitationProvider`'s four `CpmetaConfig` parameters (lines 30, 37, 47, 52) narrow to the
   shared subset.

## Verification

- `sbt compile Test/compile` green.
- Both applications start against the **unmodified** root `application.conf` and the unmodified
  `example.application.conf`.
- Diff the effective resolved config before and after (`ConfigFactory` dump at startup) for a
  production-shaped configuration file — every shared key must resolve identically.
- Deliberately corrupt a `meta`-only key and confirm `rdfStore` still starts; corrupt a
  store-only key and confirm `meta` still starts. That is the payoff for this task.
