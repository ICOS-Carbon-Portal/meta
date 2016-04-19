package se.lu.nateko.cp.meta.test.utils

import org.scalatest.FunSpec

class DateTimeUtilsTests extends FunSpec{

	//import se.lu.nateko.cp.meta.utils.DateTimeUtils._

	def dateTimeToUtc(s: String) = s

	ignore("dateTimeToUtc function"){

		it("correctly reformats a valid ISO-8601 dateTime string with time zone information"){
			val converted = dateTimeToUtc("2011-05-09T18:45:01+02:00")
			
			assert(converted === "2011-05-09T16:45:01Z")
		}

		it("leaves a valid ISO-8601 UTC dateTime string intact"){
			val original = "2011-05-09T16:45:01Z"
			assert(dateTimeToUtc(original) === original)
		}

		it("throws an UploadUserErrorException when given incomplete dateTime"){
			intercept[IllegalArgumentException](dateTimeToUtc("2011-05-09T18:45:01"))
			intercept[IllegalArgumentException](dateTimeToUtc("2011-05-09T18:45+02:00"))
			intercept[IllegalArgumentException](dateTimeToUtc("2011-05-09"))
			intercept[IllegalArgumentException](dateTimeToUtc("2011-05-09T18+02:00"))
			intercept[IllegalArgumentException](dateTimeToUtc("18:45:01+02:00"))
		}

		it("throws an UploadUserErrorException when given dateTime with fractional seconds"){
			intercept[IllegalArgumentException](dateTimeToUtc("2011-05-09T18:45:01.333+02:00"))
			intercept[IllegalArgumentException](dateTimeToUtc("2011-05-09T18:45:01,4937294+02:00"))
		}
	}
}