package se.lu.nateko.cp.meta.core.tests.crypto

import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

class Sha256SumTests extends AnyFunSuite{

	test("Hex parsing/serialization round trip"){
		val hex = "000102030405060708090a0b0c0d0e0f1011"
		assert(hex.length === 36)
		val hex2 = Sha256Sum.fromHex(hex).get.hex

		assert(hex2 === hex)
	}

	test("Base64 / hex round trip"){
		val base64Url = "dXJBtJ6YF4moFgXItQ04wOum1PJiqz8azBbhwKdtDgA"
		val hash = Sha256Sum.fromBase64Url(base64Url).get
		val hex = hash.hex
		val fromHex = Sha256Sum.fromHex(hex).get
		assert(fromHex === hash)
	}

	test("Formatting negative byte values"){
		val n: Byte = -1
		val hex = Sha256Sum.formatByte(n)
		assert(hex == "ff")
	}
}
