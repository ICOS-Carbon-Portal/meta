# 23 — Update `rdf-store-split.md`

**Phase:** 6 — cut the edge and ship two applications
**Depends on:** 21, 22

## Goal

Bring the design document in line with the code, so the next person reads an accurate account
rather than a plan that was half superseded.

## Edits required

### 1. Status and objective (`rdf-store-split.md:10`)

> The dependency direction is `meta -> rdfStore -> metaCore`; `rdfStore` does not depend on the
> `meta` application.

Becomes `meta -> rdfCommon -> metaCore` and `rdfStore -> rdfCommon -> metaCore`, with neither
application depending on the other. State the rule that `rdfCommon` must never depend on either.

### 2. Component diagram (`rdf-store-split.md:31-55`)

Add `rdfCommon` and `metaCore` as a shared base beneath both boxes. The current diagram shows
only the runtime topology; make it clear which parts are the *build* graph and which are the
*process* boundary, since they are now different shapes.

### 3. Ownership table (`rdf-store-split.md:86-97`)

The row

> | DOI, citation, landing-page composition | `meta` |

is contradicted by task 11. Split it:

| Concern | Owner |
|---|---|
| Citation and DOI metadata resolution | `rdfCommon` (both applications) |
| Landing-page composition | `meta` |

Add rows for the other shared concerns: configuration model, RDF vocabularies, the
`InstanceServer` API, the read-side object fetchers.

### 4. Configuration (`rdf-store-split.md:167`)

> The application configuration resource is owned by `rdfStore` and is inherited by `meta`
> through the build dependency.

No longer true after tasks 14–16. Describe the new layering: shared defaults in `rdfCommon`'s
`reference.conf`, per-application defaults in each application's own, operator overrides
unchanged in the working-directory `application.conf`.

If task 15 was done, document the three-way split of the configuration model and note that the
HOCON key paths were deliberately left unchanged.

### 5. Remaining implementation slices (`rdf-store-split.md:193-198`)

- Slice 3 (remote integration tests on LMDB) — mark implemented, per task 19.
- Slice 4 (split the shared configuration/domain support out of `rdfStore`) — mark implemented,
  and point at `docs/rdf-common-split/` for the record of how.
- Slices 1 (service authentication) and 2 (idempotent mutation batches) remain open; confirm
  they are still accurate.

### 6. Existing coupling (`rdf-store-split.md:12-27`)

This section describes the state before the process split. Either mark it explicitly as
historical or fold it into a short "how we got here" note — as written it reads as current.

## Also

- Add a pointer from `README.md` to both documents if one does not exist.
- Keep `docs/rdf-common-split/` in the repository as the task record; it is more useful as
  history than as a deleted branch artifact.

## Verification

Read the updated document start to finish against `build.sbt` and the module layout. Every
claim about who depends on what, who owns what, and what is implemented should be checkable
against the tree in under a minute.
