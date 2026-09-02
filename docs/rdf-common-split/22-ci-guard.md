# 22 — Add a CI guard against the dependency returning

**Phase:** 6 — cut the edge and ship two applications
**Depends on:** 21

## Goal

Make it impossible to reintroduce `meta -> rdfStore` without CI failing.

## Why

The dependency direction is the entire architectural claim of the split
(`rdf-store-split.md:10` — "The dependency direction is `meta -> rdfStore -> metaCore`;
`rdfStore` does not depend on the `meta` application"). After task 21 the claim becomes
`meta -> rdfCommon <- rdfStore`. It is a one-line change to break it, and the breakage is
invisible in a code review that is looking at Scala rather than at `build.sbt`.

## Implementation

An sbt task in `build.sbt`, run in CI:

```scala
val checkModuleBoundaries = taskKey[Unit]("Fails if the module dependency graph is violated")

checkModuleBoundaries := {
    val metaCp = (meta / Compile / dependencyClasspath).value.map(_.data.getName)
    val storeCp = (rdfStore / Compile / dependencyClasspath).value.map(_.data.getName)

    val metaViolations = metaCp.filter(_.contains("meta-rdf-store"))
    val storeViolations = storeCp.filter(n => n.contains("meta_3") || n.startsWith("meta-"))
        .filterNot(n => n.contains("meta-core") || n.contains("meta-rdf-common"))

    if metaViolations.nonEmpty then
        sys.error(s"meta must not depend on rdfStore: ${metaViolations.mkString(", ")}")
    if storeViolations.nonEmpty then
        sys.error(s"rdfStore must not depend on meta: ${storeViolations.mkString(", ")}")
}
```

Check both directions. The reverse edge is the one that would be most damaging and is currently
unguarded too.

Adapt the artifact-name matching to what the classpath actually contains — inspect
`sbt "show meta/dependencyClasspath"` output first rather than trusting the strings above.

## Wire it up

- Add to the CI workflow, alongside the existing `-Werror`-on-CI compile
  (`build.sbt:9,20`).
- Add to `cpDeployPreAssembly` (`build.sbt:161-169`) so a production build enforces it.

## Optional second guard

A source-level check is a useful complement, since it catches the violation with a more helpful
message than a classpath diff:

```
grep -rn 'se\.lu\.nateko\.cp\.meta\.rdfstore' src/ && exit 1
grep -rn 'sparql\.magic\|sparql\.index' src/main && exit 1
```

The second pattern is what task 06 fixed; keeping it as a guard stops it recurring.

## Verification

- The task passes on the current tree.
- Temporarily re-add `rdfStore` to `meta`'s `dependsOn` and confirm the task fails with a clear
  message. Revert.
