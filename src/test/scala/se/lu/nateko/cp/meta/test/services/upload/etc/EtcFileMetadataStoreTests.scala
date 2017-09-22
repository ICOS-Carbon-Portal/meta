package se.lu.nateko.cp.meta.test.services.upload.etc

import org.scalatest.FunSpec
import spray.json._
import se.lu.nateko.cp.meta.ingestion.badm.Parser
import se.lu.nateko.cp.meta.test.ingestion.badm.BadmTestHelper
import se.lu.nateko.cp.meta.ingestion.badm.BadmEntry
import se.lu.nateko.cp.meta.services.upload.etc.EtcFileMetadataStore

class EtcFileMetadataStoreTests extends FunSpec {

	def getBadmEntries: Seq[BadmEntry] = {
		val json = BadmTestHelper.getIcosMetaJson.parseJson.asJsObject
		Parser.parseEntriesFromEtcJson(json)
	}

	def getMeta = EtcFileMetadataStore(getBadmEntries)

	describe("BADM parser"){
		it("parses the FA-Lso JSON correctly"){
			assert(getBadmEntries.size === 56)
		}
	}

	describe("EtcFileMetadataStore file lookup"){
		it("Finds existing file info by station id/logger id/file id"){
			//val meta = getMeta
			//test code goes here
			pending
		}
	}

	describe("EtcFileMetadataStore logger lookup"){
		it("Finds existing logger info by station id/logger id"){
			//val meta = getMeta
			//test code goes here
			pending
		}
	}

	describe("EtcFileMetadataStore UTC offset lookup"){
		it("Finds existing station UTC offset by station id"){
			//val meta = getMeta
			//test code goes here
			pending
		}
	}
}
