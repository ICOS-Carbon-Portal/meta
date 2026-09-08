# 30 — Move single-application members out of shared files

**Phase:** 7 — tighten the boundary
**Depends on:** 10, 12

## Goal

Relocate members of otherwise-legitimately-shared `rdfCommon` files that only one application
uses, and delete the dead ones.

## Why

These are not whole files in the wrong module — the enclosing files are genuinely shared. They
are individual declarations that drifted in, and each one is a small piece of one application's
domain sitting in the shared library where the other application compiles against it.

| Member | file | used by | action |
|---|---|---|---|
| `RdfRetraction` | `instanceserver/RdfUpdate.scala` | nobody | delete |
| `RdfAssertion` | `instanceserver/RdfUpdate.scala` | meta only (`metaflow/RdfDiffCalc.scala:100`) | move to meta |
| `Acquisition`, `Submission`, `NextVersColl`, `VarInfo` | `services/CpVocab.scala` | rdfStore only | move to rdfStore |
| `Mergeable` + `Validated.merge` | `utils/Mergeable.scala`, `utils/Validated.scala:142` | rdfStore only (`CitationClient.scala:123,143`) | move to rdfStore |
| `PersonRole`, `RoleDetails`, `Membership` | `services/attribution/AttributionProvider.scala` | meta only | assess |

`AttributionProvider` itself is used by both, so its companion members need a closer look than
the others: check what the class's own methods return before moving anything out of it. If
`getMemberships` returns `Membership`, the type cannot move even though only meta names it.
Record the finding either way; a member that must stay is worth a comment saying why.

`Validated.merge` is the interesting one: it is the only reason `Mergeable` exists, and it is a
method on a heavily-shared type. Moving it means an extension method in rdfStore rather than a
member of `Validated`.

## Steps

Work member by member, compiling between each. Prefer keeping the same package name in the
destination module so call sites do not change.

## Outcome

Moved: `RdfRetraction` deleted (dead), `RdfAssertion` to
`src/main/scala/instanceserver/RdfAssertion.scala`, `Mergeable` and `Validated.merge` to
`rdfstore/src/main/scala/utils/Mergeable.scala` (the latter as `Mergeable.mergeValidated`, since
it can no longer be a member of the shared `Validated` companion).

Assessed and kept, with a comment on each explaining why:

- The `CpVocab` extractors are the exact inverses of minting methods in the same object and share
  its private `asPrefWithHash` helpers and prefix constants. `CpVocab.DataObject`, which the
  original census missed, is rdfStore-only for the same reason and belongs to the same group.
  Moving four of five out would split one URI-naming scheme across two modules.
- All three `AttributionProvider` companion types are in the public signature of the shared class
  (`getMemberships`, `getPersonRoles`), and `RoleDetails`/`Membership` drive its own filtering and
  orderings. Only meta *names* them, but they cannot leave.

## Verification

- `sbt rdfCommon/Test/compile rdfStore/Test/compile Test/compile` green after each move.
- Re-run the reference census (grep each name across the three modules) and confirm the moved names no longer appear
  in `rdf-common/src`.
