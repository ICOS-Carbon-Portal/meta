package se.lu.nateko.cp.meta.tags

import org.scalatest.Tag

object SlowRoute extends Tag("tags.SlowRoute")

/**
 * Marks tests that fork a real `rdfStore` process (and a throwaway PostgreSQL instance) and
 * drive it over HTTP with an LMDB-backed store. See task 19 in docs/rdf-common-split: this is
 * "the gate for the whole split" but is deliberately excluded from fast local `test` runs
 * (see meta's `Test / testOptions` in build.sbt) because it needs `initdb`/`pg_ctl` on PATH
 * and takes several seconds to boot two processes. It is run explicitly as part of
 * `cpDeployPreAssembly`, so it can never be skipped before a production build.
 */
object RemoteIntegration extends Tag("tags.RemoteIntegration")
