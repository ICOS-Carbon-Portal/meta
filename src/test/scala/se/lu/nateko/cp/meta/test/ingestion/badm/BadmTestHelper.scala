package se.lu.nateko.cp.meta.test.ingestion.badm

import java.io.InputStreamReader

import scala.collection.immutable.Stream.consWrapper

import com.opencsv.CSVReader

import se.lu.nateko.cp.meta.ingestion.badm.BadmSchema


object BadmTestHelper {

	def getSchema = BadmSchema.parseSchemaFromCsv(
		getRows("/variablesHarmonized_OTC_CP.csv"),
		getRows("/variablesHarmonizedVocab_OTC_CP.csv")
	)

	private def getRows(path: String): Seq[Array[String]] = {
		val stream = getClass.getResourceAsStream(path)
		val reader = new InputStreamReader(stream)
		val csvReader = new CSVReader(reader, ',', '"')
		val iter = csvReader.iterator()

		def getStream: Stream[Array[String]] = {
			if(iter.hasNext) iter.next() #:: getStream
			else {
				csvReader.close()
				Stream.empty
			}
		}
		getStream.drop(1)
	}

}
