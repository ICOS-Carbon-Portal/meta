package se.lu.nateko.cp.meta.test.ingestion.badm

import java.time.LocalDate

import scala.io.Source

import org.scalatest.FunSpec

import se.lu.nateko.cp.meta.ingestion.badm.BadmLocalDate
import se.lu.nateko.cp.meta.ingestion.badm.Parser._

class ParserTests extends FunSpec{

	private def getSource: Source = {
		val url = getClass.getResource("/AncillaryCP_117_20160321.csv")
		Source.fromURL(url)
	}

	describe("toBadmDate"){
		it("Parses BADM date string correctly"){
			val expected = BadmLocalDate(LocalDate.parse("2001-03-05"))
			val actual = toBadmDate("20010305")
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