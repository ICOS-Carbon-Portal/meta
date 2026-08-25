package se.lu.nateko.cp.meta.test

import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.{AppConfig, ConfigLoader}

class ConfigLoaderTests extends AnyFunSpec:

	describe("meta configuration"):
		it("reads fields shared with rdfStore from their common cpmeta paths"):
			val root = AppConfig.rootConfWithWorkingDirOverrides
			val config = ConfigLoader.default

			assert(config.rdfLog.server.host === root.getString("cpmeta.rdfLog.server.host"))
			assert(config.rdfLog.credentials.db === root.getString("cpmeta.rdfLog.credentials.db"))
			assert(config.instanceServers.specific("labelingForAdmin").skipLogIngestionAtStart.contains(
				root.getBoolean("cpmeta.instanceServers.specific.labelingForAdmin.skipLogIngestionAtStart")
			))
			assert(config.dataUploadService.collectionServers(Envri.ICOS) ===
				root.getString("cpmeta.dataUploadService.collectionServers.ICOS"))
			assert(config.dataUploadService.documentServers(Envri.SITES) ===
				root.getString("cpmeta.dataUploadService.documentServers.SITES"))
			assert(config.dataUploadService.handle.prefix(Envri.ICOS) ===
				root.getString("cpmeta.dataUploadService.handle.prefix.ICOS"))
			assert(config.citations.doi.restEndpoint.toString ===
				root.getString("cpmeta.citations.doi.restEndpoint"))

end ConfigLoaderTests
