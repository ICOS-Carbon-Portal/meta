package se.lu.nateko.cp.meta.persistence

import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.persistence.RdfLogManager.{ReplayPolicy, RestoreResult}
import se.lu.nateko.cp.meta.services.citation.{
	StoreDataObjectServerDefinition,
	StoreDataObjectServersConfig,
	StoreInstanceServerConfig,
	StoreInstanceServersConfig
}

import java.net.URI

class RdfLogManagerTest extends AnyFunSpec:

	describe("RDF-log replay policy"):
		it("restores only when the replay was requested on the command line"):
			val policy = ReplayPolicy()
			assert(policy.shouldRestore(restoreRequested = true))
			assert(!policy.shouldRestore(restoreRequested = false))

		it("honours an explicit request not to skip restoration"):
			val policy = ReplayPolicy(skipAtStart = Some(false))
			assert(policy.shouldRestore(restoreRequested = true))
			assert(!policy.shouldRestore(restoreRequested = false))

		it("honours an explicit request to skip restoration"):
			val policy = ReplayPolicy(skipAtStart = Some(true))
			assert(!policy.shouldRestore(restoreRequested = true))
			assert(!policy.shouldRestore(restoreRequested = false))

		it("does not let an offset implicitly enable restoration"):
			val policy = ReplayPolicy(fromId = Some(42))
			assert(policy.shouldRestore(restoreRequested = true))
			assert(!policy.shouldRestore(restoreRequested = false))

	describe("RDF-log restore result"):
		it("does not invalidate a saved index when no log replay was attempted"):
			assert(!RestoreResult(attemptedLogs = 0).invalidatesIndex)

		it("invalidates a saved index after any attempted log replay"):
			assert(RestoreResult(attemptedLogs = 1).invalidatesIndex)

	describe("instance-server replay configuration"):
		val context = URI.create("https://example.test/graph/").nn

		it("excludes instance servers configured to skip log ingestion"):
			val config = StoreInstanceServersConfig(
				specific = Map(
					"writer" -> StoreInstanceServerConfig(context, None, Some("shared"), None, None),
					"alias" -> StoreInstanceServerConfig(context, None, Some("shared"), Some(true), None)
				),
				forDataObjects = Map.empty
			)

			val logs = RdfLogManager.configuredLogs(config)
			assert(logs.map(_.name) === Seq("shared"))

		it("turns a data-object replay offset into an explicit regular-start replay"):
			val config = StoreInstanceServersConfig(
				specific = Map.empty,
				forDataObjects = Map(
					Envri.ICOS -> StoreDataObjectServersConfig(
						commonReadContexts = Seq.empty,
						uriPrefix = URI.create("https://example.test/objects/").nn,
						definitions = Seq(StoreDataObjectServerDefinition(
							label = "data", format = URI.create("https://example.test/format").nn, replayLogFrom = Some(42)
						))
					)
				)
			)

			val log = RdfLogManager.configuredLogs(config).head
			assert(log.fromId.contains(42))
			assert(log.skipAtStart.contains(false))

end RdfLogManagerTest
