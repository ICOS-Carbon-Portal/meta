package se.lu.nateko.cp.meta.test.services.upload.etc

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.etcupload.{DataType, StationId}
import spray.json._
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import se.lu.nateko.cp.meta.test.ingestion.badm.BadmTestHelper
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry
import se.lu.nateko.cp.meta.services.upload.etc._

class EtcFileMetadataStoreTests extends AnyFunSpec {

	def getBadmEntries: Seq[BadmEntry] = {
		val json = BadmTestHelper.getIcosMetaJson.parseJson
		Parser.parseEntriesFromEtcJson(json)
	}

	def getMeta = EtcFileMetadataStore(getBadmEntries)
	val StationId(falsoId) = "FA-Lso"

	describe("BADM parser"){
		it("parses the FA-Lso JSON correctly"){
			assert(getBadmEntries.size === 57)
		}
	}

	describe("EtcFileMetadataStore file lookup"){
		it("Finds existing file info by station id/logger id/file id"){
			val meta = getMeta
			val file = meta.lookupFile(EtcFileMetaKey(falsoId, loggerId = 2, fileId = 1, DataType.EC)).get

			assert(file === EtcFileMeta(DataType.EC, isBinary = true))
		}
	}

	describe("EtcFileMetadataStore UTC offset lookup"){
		it("Finds existing station UTC offset by station id"){
			val meta = getMeta
			val utcOffset = meta.getUtcOffset(falsoId)
			assert(utcOffset === Some(4))
		}
	}
}
