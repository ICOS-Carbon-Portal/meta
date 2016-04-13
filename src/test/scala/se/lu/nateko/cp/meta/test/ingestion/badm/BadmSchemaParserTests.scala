package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.FunSpec
import scala.io.Source
import se.lu.nateko.cp.meta.ingestion.badm.BadmSchemaParser
import java.io.InputStreamReader
import com.opencsv.CSVReader

class BadmSchemaParserTests extends FunSpec{

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

	describe("parseSchemaFromCsv"){

		def getSchema = BadmSchemaParser.parseSchemaFromCsv(
			getRows("/variablesHarmonized_OTC_CP.csv"),
			getRows("/variablesHarmonizedVocab_OTC_CP.csv")
		)

		it("Parses the test BADM schema successfully"){
			val schema = getSchema
			assert(schema.properties.size === 51)
			//schema.vocabs.keys.foreach(println)
			assert(schema.vocabs.size === 20)
		}
	}
}