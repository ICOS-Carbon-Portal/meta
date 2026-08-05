# 16 — Give `meta` its own `AppConfig` and reference defaults

**Phase:** 4 — configuration
**Depends on:** 14 (15 if done)

## Goal

Remove `meta`'s import of a type in the `...meta.rdfstore` package, and give each application
its own classpath configuration defaults.

## Why

`src/main/scala/Main.scala` currently reads:

```scala
import se.lu.nateko.cp.meta.rdfstore.AppConfig                          // line 10
given system: ActorSystem = ActorSystem("cpmeta", config = AppConfig.rootConfWithWorkingDirOverrides)  // line 19
```

`meta` reaches into the store's package purely to build its own ActorSystem's root config. Even
after task 14 moves the class to `rdfCommon`, the ownership question remains: `application.conf`
is currently shipped in `rdfstore/src/main/resources/`, and `rdf-store-split.md:167` describes
it as "owned by `rdfStore` and inherited by `meta` through the build dependency". That
inheritance is exactly the dependency this plan removes.

## Steps

1. After task 14, `AppConfig` lives in `rdfCommon` in package `se.lu.nateko.cp.meta`. Keep its
   layering logic — system properties > working-directory `application.conf` > classpath
   defaults > dependency `reference.conf`s — exactly as documented in its scaladoc. That
   behaviour is relied on by deployment and must not drift.
2. Convert the shipped classpath defaults from `application.conf` to `reference.conf`, split by
   owner:
   - `rdf-common/src/main/resources/reference.conf` — shared defaults
   - `rdfstore/src/main/resources/reference.conf` — the `rdfStore { ... }` section and
     store-side `cpmeta.*` defaults
   - `src/main/resources/reference.conf` — `meta`-side `cpmeta.*` defaults

   `reference.conf` is the right layer for library-shipped defaults; `application.conf` on the
   classpath competes with the operator's working-directory file, which is why `AppConfig` has
   to do the manual overlay dance in the first place. Verify the resulting precedence carefully
   — this is a behaviour change in the config layering even though no key moves.
3. Update `src/main/scala/Main.scala:10` to the new package.
4. Leave the root `application.conf` and `example.application.conf` untouched.

## Verification

- `grep -rn 'meta.rdfstore' src/main/scala` returns nothing.
- Both applications start against the existing root `application.conf`.
- Dump and diff the resolved config for both applications before and after; only ownership of
  the defaults should change, never a resolved value.
- Confirm the `-Dconfig.file` / `-Dconfig.resource` / `-Dconfig.url` escape hatch still bypasses
  the working-directory lookup as documented.
