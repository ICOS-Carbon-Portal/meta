package se.lu.nateko.cp.meta.test.ingestion.badm

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.ingestion.badm.Parser._
import java.time.LocalDate
import scala.io.Source

class ParserTests extends FunSpec{

	private def getSource: Source = {
		val url = getClass.getResource("/AncillaryCP_117_20160321.txt")
		Source.fromURL(url)
	}

	describe("toIsoDate"){
		it("Parses BADM date string correctly"){
			val expected = LocalDate.parse("2001-03-05")
			val actual = toIsoDate("20010305")
			assert(actual === expected)
		}
	}

	describe("parseEntriesFromCsv"){
		it("Parses the test BADM CSV file successfully"){
			val src = getSource
			val entries = parseEntriesFromCsv(src)
			assert(entries.size === 95)
			//entries.foreach(println)
		}
	}
}