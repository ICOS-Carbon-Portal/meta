# 28 — Delete `CachedSourceTests`

**Phase:** 7 — tighten the boundary
**Depends on:** 18

## Goal

Delete `rdf-common/src/test/scala/utils/streams/CachedSourceTests.scala`.

## Why

There is no `CachedSource` anywhere in the repository — the suite is named after a type that no
longer exists. What it actually asserts is that akka's own `Sink.queue`/`Source.queue` pair
behaves as documented, which is a test of akka, not of this codebase. The single assertion is
also inside an `onComplete` callback that the test never waits on, so it cannot fail the build.

It is additionally the *only* reason rdf-common's test run constructs an `ActorSystem`, which
resolves every `reference.conf` on the classpath. That coupling has already needed a stub config
once and an explanatory comment twice as the config split moved defaults around; deleting the
suite removes the constraint rather than re-documenting it.

## Steps

1. `git rm rdf-common/src/test/scala/utils/streams/CachedSourceTests.scala`
2. Remove the now-empty `utils/streams` directory from the test tree.
3. Check whether anything else in rdf-common's test tree needs akka-stream (task 31 acts on the
   answer).

## Verification

- `sbt rdfCommon/test` green, with the suite count down by one and no `ActorSystem` started.
