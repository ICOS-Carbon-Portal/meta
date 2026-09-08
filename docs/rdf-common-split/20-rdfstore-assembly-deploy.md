# 20 — Give `rdfStore` assembly and deploy configuration

**Phase:** 6 — cut the edge and ship two applications
**Depends on:** 17

## Goal

Make `rdfStore` actually deployable. Today it can only be started from sbt.

## Why

`rdf-store-split.md:5` states the target is "two independently deployable JVM applications",
but the `rdfStore` project block (`build.sbt:268-300`) has:

- `Compile / mainClass := Some("se.lu.nateko.cp.meta.rdfstore.Main")`
- `reStart / baseDirectory` pointed at the root
- **no `assembly` configuration** — no merge strategy, no fat jar
- **no `cpDeploy*` settings** — no target, no playbook, no permitted inventories
- **no `IcosCpSbtDeployPlugin`**

So there is no artifact to deploy and no deployment pipeline. Until this is fixed,
"independently deployable" is aspirational.

## Changes

Add to the `rdfStore` project:

```scala
.enablePlugins(IcosCpSbtDeployPlugin)
```

and settings:

- **`assembly / assemblyMergeStrategy`** — `meta`'s block (`build.sbt:184-195`) is the template.
  `rdfStore` needs at least the `module-info.class` discard and `application.conf` concat rules.
  It will not need the `-fastopt.js` discard (no Scala.js) and probably not the axiom.xml /
  Guava POM rules (those come from OWL API, which `rdfStore` does not depend on). Start from
  `meta`'s and delete what does not apply, then confirm the jar builds.
- **`assembly / assemblyRepeatableBuild := false`** if build times demand it, matching `meta`.
- **`cpDeployTarget`** — a *distinct* value from `meta`'s `"cpmeta"`, e.g. `"cpmetardfstore"`.
  Getting this wrong means deploying one service over the other. Confirm the name with whoever
  owns the Ansible inventories before using it.
- **`cpDeployBuildInfoPackage := "se.lu.nateko.cp.meta.rdfstore"`**
- **`cpDeployPlaybook`** — a new playbook, since `meta`'s `"core.yml"` provisions the metadata
  service. The `rdfStore` playbook must mount the RDF storage volume; `meta`'s must **not**
  (`rdf-store-split.md:57` — "Only `rdfStore` may mount the RDF storage directory").
- **`cpDeployPermittedInventories := Some(Seq("production", "staging", "cities"))`**, matching
  `meta`.
- **`cpDeployInfraBranch := "master"`**

## Also update `meta`'s pre-assembly

`build.sbt:161-169` runs `metaCore / Test / test` and `Test / test`. Add
`rdfCommon / Test / test` and `rdfStore / Test / test` so a production build of `meta` cannot
pass while the modules it ships against are broken.

## Deployment ordering

Document, in the new playbook or in `rdf-store-split.md`, that `rdfStore` must be started
before `meta`: `meta`'s startup runs a bounded readiness query against the remote repository
(`rdf-store-split.md:132-138`). Also note the reverse-proxy requirement — `<meta host>/sparql`
routes to `rdfStore`, with `X-Forwarded-For` overwritten with the trusted client address
(`rdf-store-split.md:57`).

## Verification

- `sbt rdfStore/assembly` produces a runnable fat jar.
- `java -jar` on that jar starts the service against the root `application.conf`.
- A dry-run deploy to `staging` targets the intended host group and mounts the RDF volume.
- A dry-run deploy of `meta` does **not** mount the RDF volume.
