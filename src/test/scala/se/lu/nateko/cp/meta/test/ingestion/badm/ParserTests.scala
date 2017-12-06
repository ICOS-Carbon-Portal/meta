package se.lu.nateko.cp.meta.test.ingestion.badm

import java.time.LocalDate
import java.time.LocalDateTime

import org.scalatest.FunSpec

import se.lu.nateko.cp.meta.ingestion.badm._
import se.lu.nateko.cp.meta.ingestion.badm.Parser._

class ParserTests extends FunSpec{

	describe("toBadmDate"){
		it("Parses BADM date string correctly"){
			val expected = BadmLocalDate(LocalDate.parse("2001-03-05"))
			val actual = toBadmDate("20010305")
			assert(actual === expected)
		}

		it("Parses BADM datetime string with seconds correctly"){
			val expected = BadmLocalDateTime(LocalDateTime.parse("2001-03-05T13:45:28"))
			val actual = toBadmDate("20010305134528")
			assert(actual === expected)
		}

		it("Parses BADM datetime string without seconds correctly"){
			val expected = BadmLocalDateTime(LocalDateTime.parse("2001-03-05T13:45"))
			val actual = toBadmDate("200103051345")
			assert(actual === expected)
		}
	}

	describe("parseEntriesFromCsv"){
		it("Parses the test BADM CSV file successfully"){
			val badmSource = BadmTestHelper.getBadmSource
			val entries = parseEntriesFromCsv(badmSource)
			assert(entries.size === 95)
			//entries.foreach(println)
		}
	}
}