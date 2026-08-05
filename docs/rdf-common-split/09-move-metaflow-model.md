# 09 — Move the metaflow model to `meta`

**Phase:** 2 — break the metaflow/vocabulary knot
**Depends on:** 07, 08
**Blocks:** 10

## Goal

Reunite the metaflow domain model with the metaflow implementations that already live in
`meta`.

## Why

The model types sit in `rdfStore` while everything that uses them sits in `meta`. `src/main`
references them heavily — `TC` (15), `TcConf` (12), `TcState` (8), `Membership` (8),
`TcStation` (7), `TcPerson` (6), `TcInstrument` (6), `Role` (8), `PI` (7), plus `TcId`,
`TcOrg`, `TcPlainOrg`, `TcGenericOrg`, `TcNetwork`, `TcFunder`, `TcFunding`, `AssumedRole`,
`InstrumentDeployment`, `Entity`, `CpIdSwapper`, `TcMetaSource`, `ETC`, `ATC`, `OTC`,
`IcosTC`, `AtcConf`, `EtcConf`.

Metadata flows are a `meta` concern by every line of the ownership table in
`rdf-store-split.md`. After tasks 07 and 08, nothing in `rdfStore` refers to these types.

## Files to move

From `rdfstore/src/main/scala/` to `src/main/scala/`:

```
metaflow/TcMetadata.scala
metaflow/Roles.scala
metaflow/icos/IcosTcConf.scala
```

They join the implementations already there: `MetaFlow.scala`, `RdfDiffCalc.scala`,
`RdfMaker.scala`, `RdfReader.scala`, `RolesDiffCalc.scala`, `StateDiffApplier.scala`,
`TriggeredMetaSource.scala`, `FileDropMetaSource.scala`, `icos/{Atc,Etc,Otc}MetaSource.scala`,
`icos/IcosMetaFlow.scala`, `cities/*`.

## Steps

1. Verify the precondition:
   `grep -rln 'metaflow' rdfstore/src/main/scala | grep -v '/metaflow/'` returns nothing.
2. `git mv` the three files. Packages are unchanged
   (`se.lu.nateko.cp.meta.metaflow`, `...metaflow.icos`).
3. Confirm the metaflow configuration case classes — `MetaFlowConfig`, `IcosMetaFlowConfig`,
   `CitiesMetaFlowConfig`, `MetaUploadConf`, `EtcConfig` — are *not* moved here. They are in
   `CpmetaConfig.scala` and are handled by tasks 14 and 15.
4. `TcMetadata.scala` calls `CpVocab.instrCpId`, which task 08 relocated to `TcVocab` in `meta` —
   update that call site.
5. Compile.

## Verification

- `sbt compile Test/compile` green.
- `find rdfstore/src/main/scala/metaflow -name '*.scala'` returns nothing; remove the empty
  directory.
- `src/test/scala/test/icos/{RdfDiffCalcTests, RolesDiffCalcTests, EtcMetaSourceTests}.scala`
  and `test/metaflow/cities/MidLowCostMetaSourceTest.scala` pass unchanged.
