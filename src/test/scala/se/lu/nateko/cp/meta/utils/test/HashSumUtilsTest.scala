package se.lu.nateko.cp.meta.utils.test

import org.scalatest.FunSpec

class HashSumUtilsTest extends FunSpec{

	import se.lu.nateko.cp.meta.utils.HashSumUtils._

	describe("ensureSha256 function"){
		val correct = "10b5accc9f24a305e15c4c034085ea64c7ec7a5b78329adc8f00983814c8d456"

		it("returns its input if the latter is a 32-byte lowercase hexadecimal string"){
			assert(ensureSha256(correct).get === correct)
		}

		it("converts its input to lowercase if the latter is a 32-byte uppercase hexadecimal string"){
			assert(ensureSha256(correct.toUpperCase).get === correct)
		}
		
		it("throws an UploadUserErrorException for an invalid input"){
			intercept[IllegalArgumentException](ensureSha256("123X").get)
			intercept[IllegalArgumentException](ensureSha256(Array.fill(32)("zz").mkString).get)
			intercept[IllegalArgumentException](ensureSha256(correct.tail).get)
			intercept[IllegalArgumentException](ensureSha256(correct + "1").get)
		}
	}
}