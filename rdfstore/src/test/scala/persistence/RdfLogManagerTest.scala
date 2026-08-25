package se.lu.nateko.cp.meta.persistence

import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.persistence.RdfLogManager.ReplayPolicy
import se.lu.nateko.cp.meta.services.citation.{
	StoreDataObjectServerDefinition,
	StoreDataObjectServersConfig,
	StoreInstanceServerConfig,
	StoreInstanceServersConfig
}

import java.net.URI

class RdfLogManagerTest extends AnyFunSpec:

	describe("RDF-log replay policy"):
		it("defaults to restoring only a fresh store"):
			val policy = ReplayPolicy()
			assert(policy.shouldRestore(isFreshStore = true))
			assert(!policy.shouldRestore(isFreshStore = false))

		it("honours an explicit request to restore at every startup"):
			val policy = ReplayPolicy(skipAtStart = Some(false))
			assert(policy.shouldRestore(isFreshStore = true))
			assert(policy.shouldRestore(isFreshStore = false))

		it("honours an explicit request to skip restoration at every startup"):
			val policy = ReplayPolicy(skipAtStart = Some(true))
			assert(!policy.shouldRestore(isFreshStore = true))
			assert(!policy.shouldRestore(isFreshStore = false))

		it("does not let an offset implicitly enable regular-start restoration"):
			val policy = ReplayPolicy(fromId = Some(42))
			assert(policy.shouldRestore(isFreshStore = true))
			assert(!policy.shouldRestore(isFreshStore = false))

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
