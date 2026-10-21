package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.ConfigLoader.given

class ConfigLoaderTests extends AnyFunSpec:

	describe("shared application configuration"):
		it("parses the packaged defaults"):
			val config = ConfigFactory.defaultApplication()
				.withFallback(ConfigFactory.defaultReferenceUnresolved()).resolve()
				.getValue("cpmeta").parseAs[CpmetaConfig]

			assert(config.port === 9094)
			assert(config.rdfStore.port === 9095)
			assert(config.instanceServers.specific("instanceschema").ingestion.exists(
				_.ingesterId == "cpMetaOnto"
			))

		it("honours the original storage, citation and administrator configuration paths"):
			val config = ConfigFactory.parseString("""
				cpmeta.rdfStorage.path = "/configured/store"
				cpmeta.citations.eagerWarmUp = false
				cpmeta.citations.style = "configured-style"
				cpmeta.sparql.adminUsers = ["admin@example.test"]
				cpmeta.rdfStore.port = 19095
			""")
				.withFallback(ConfigFactory.defaultApplication())
				.withFallback(ConfigFactory.defaultReferenceUnresolved()).resolve()
				.getValue("cpmeta").parseAs[CpmetaConfig]

			assert(config.rdfStorage.path === "/configured/store")
			assert(!config.citations.eagerWarmUp)
			assert(config.citations.style === "configured-style")
			assert(config.sparql.adminUsers === Seq("admin@example.test"))
			assert(config.rdfStore.port === 19095)

		it("derives the rdfStore endpoints from the rdfStore host and port"):
			val config = ConfigFactory.parseString("""
				cpmeta.rdfStore.httpBindInterface = "10.0.0.7"
				cpmeta.rdfStore.port = 19095
			""")
				.withFallback(ConfigFactory.defaultApplication())
				.withFallback(ConfigFactory.defaultReferenceUnresolved()).resolve()
				.getValue("cpmeta").parseAs[CpmetaConfig]

			val rdfStore = config.rdfStore
			assert(rdfStore.sparqlEndpoint.toString === "http://10.0.0.7:19095/internal/sparql")
			assert(rdfStore.adminEndpoint.toString === "http://10.0.0.7:19095/admin/read-only")
			assert(rdfStore.derivedMetadataEndpoint.toString === "http://10.0.0.7:19095/internal/derived/v1/resolve")
