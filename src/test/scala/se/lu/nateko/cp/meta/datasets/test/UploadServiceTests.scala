package se.lu.nateko.cp.meta.datasets.test

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.datasets.UploadService
import se.lu.nateko.cp.meta.datasets.UploadUserErrorException

class UploadServiceTests extends FunSpec{

	import UploadService._

	describe("dateTimeToUtc function"){

		it("correctly reformats a valid ISO-8601 dateTime string with time zone information"){
			val converted = UploadService.dateTimeToUtc("2011-05-09T18:45:01+02:00")
			
			assert(converted === "2011-05-09T16:45:01Z")
		}

		it("leaves a valid ISO-8601 UTC dateTime string intact"){
			val original = "2011-05-09T16:45:01Z"
			assert(dateTimeToUtc(original) === original)
		}

		it("throws an UploadUserErrorException when given incomplete dateTime"){
			intercept[UploadUserErrorException](dateTimeToUtc("2011-05-09T18:45:01"))
			intercept[UploadUserErrorException](dateTimeToUtc("2011-05-09T18:45+02:00"))
			intercept[UploadUserErrorException](dateTimeToUtc("2011-05-09"))
			intercept[UploadUserErrorException](dateTimeToUtc("2011-05-09T18+02:00"))
			intercept[UploadUserErrorException](dateTimeToUtc("18:45:01+02:00"))
		}

		it("throws an UploadUserErrorException when given dateTime with fractional seconds"){
			intercept[UploadUserErrorException](dateTimeToUtc("2011-05-09T18:45:01.333+02:00"))
			intercept[UploadUserErrorException](dateTimeToUtc("2011-05-09T18:45:01,4937294+02:00"))
		}
	}
	
	describe("ensureSha256 function"){
		val correct = "10b5accc9f24a305e15c4c034085ea64c7ec7a5b78329adc8f00983814c8d456"

		it("returns its input if the latter is a 32-byte lowercase hexadecimal string"){
			assert(ensureSha256(correct) === correct)
		}

		it("converts its input to lowercase if the latter is a 32-byte uppercase hexadecimal string"){
			assert(ensureSha256(correct.toUpperCase) === correct)
		}
		
		it("throws an UploadUserErrorException for an invalid input"){
			intercept[UploadUserErrorException](ensureSha256("123X"))
			intercept[UploadUserErrorException](ensureSha256(Array.fill(32)("zz").mkString))
			intercept[UploadUserErrorException](ensureSha256(correct.tail))
			intercept[UploadUserErrorException](ensureSha256(correct + "1"))
		}
	}
}