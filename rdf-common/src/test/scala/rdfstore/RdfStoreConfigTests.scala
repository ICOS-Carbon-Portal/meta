package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.cpauth.core.ConfigLoader.parseAs
import se.lu.nateko.cp.meta.ConfigLoader.given
import se.lu.nateko.cp.meta.CpmetaConfig

class RdfStoreConfigTests extends AnyFunSpec:

	describe("RdfStoreConfig"):
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
			assert(rdfStore.vanillaSparqlEndpoint.toString === "http://10.0.0.7:19095/internal/vanilla/sparql")
			assert(rdfStore.adminEndpoint.toString === "http://10.0.0.7:19095/admin/read-only")
			assert(rdfStore.derivedMetadataEndpoint.toString === "http://10.0.0.7:19095/internal/derived/v1/resolve")
			assert(rdfStore.dropDoiCacheEndpoint("10.18160/ABCD-1234").toString ===
				"http://10.0.0.7:19095/internal/derived/v1/drop-cache/10.18160/ABCD-1234")
