# 26 — Stop running `rdfStore`'s tests in `meta`'s deploy gate

**Phase:** 7 — tighten the boundary
**Depends on:** 21, 22

## Goal

Remove `rdfStore / Test / test` from `meta`'s `cpDeployPreAssembly` (`build.sbt:189`).

## Why

The line is residue from `meta.dependsOn(rdfStore)`, removed in task 21. The two are now
independently deployable services with no build edge between them, but a `meta` release still
gates on rdfStore's full suite — which needs LMDB fixtures and a Postgres cluster. A store-side
failure blocks a meta deploy for code that is not in meta's artifact, and the fixtures slow the
gate down for no coverage gain.

`rdfStore`'s own gate (`build.sbt:319-325`) is already correctly scoped: `metaCore`, `rdfCommon`,
itself. `meta`'s should be the mirror image: `metaCore`, `rdfCommon`, itself.

Note this is *not* the same as dropping the boundary check — `checkModuleBoundaries` (task 22)
stays in both gates. Running the other service's tests is not what enforces the boundary.

## Steps

1. Delete the `rdfStore / Test / test,` line from `meta`'s `cpDeployPreAssembly`.
2. Leave `metaCore / Test / test` and `rdfCommon / Test / test` — those are real dependencies.
3. Check whether `rdfStore / clean` should have been in the sequence too; if the tests go, so
   does any reason to clean that project from meta's gate.

## Verification

- `sbt "show meta/cpDeployPreAssembly"` no longer references rdfStore, or inspect the setting.
- `rdfStore`'s own gate is untouched.
