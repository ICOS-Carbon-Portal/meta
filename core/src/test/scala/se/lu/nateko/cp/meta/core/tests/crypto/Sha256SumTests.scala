package se.lu.nateko.cp.meta.core.tests.crypto

import org.scalatest.FunSuite
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

class Sha256SumTests extends FunSuite{

	test("Hex parsing/serialization round trip"){
		val hex = "000102030405060708090a0b0c0d0e0f1011"
		assert(hex.length === 36)
		val hex2 = Sha256Sum.fromHex(hex).get.hex

		assert(hex2 === hex)
	}
}
