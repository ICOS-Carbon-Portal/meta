package se.lu.nateko.cp.meta.test.ingestion.badm

import java.io.InputStreamReader

import scala.collection.immutable.Stream.consWrapper

import com.opencsv.CSVReader

import se.lu.nateko.cp.meta.ingestion.badm.BadmSchema
import scala.io.Source
import se.lu.nateko.cp.meta.ingestion.badm.Parser


object BadmTestHelper {

	def getSchema: BadmSchema.Schema = BadmSchema.parseSchemaFromCsv(
		getClass.getResourceAsStream("/variablesHarmonized_OTC_CP.csv"),
		getClass.getResourceAsStream("/variablesHarmonizedVocab_OTC_CP.csv")
	)

	def getBadmSource: java.io.InputStream = {
		getClass.getResourceAsStream("/AncillaryCP_117_20160321.csv")
	}

}
