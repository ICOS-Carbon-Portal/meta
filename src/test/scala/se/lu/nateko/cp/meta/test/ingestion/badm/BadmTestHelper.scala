package se.lu.nateko.cp.meta.test.ingestion.badm

import java.nio.charset.StandardCharsets
import org.apache.commons.io.IOUtils
import se.lu.nateko.cp.meta.ingestion.badm.BadmSchema

object BadmTestHelper {

	def getSchema: BadmSchema.Schema = BadmSchema.parseSchemaFromCsv(
		getResAsString("/variablesHarmonized_OTC_CP.csv"),
		getResAsString("/variablesHarmonizedVocab_OTC_CP.csv")
	)

	def getBadmSource: String = {
		getResAsString("/AncillaryCP_117_20160321.csv")
	}

	def getIcosMetaJson: String = getResAsString("/falsometa.json")

	def getResAsString(path: String): String = IOUtils.toString(getClass.getResourceAsStream(path), StandardCharsets.UTF_8)
}
