package se.lu.nateko.cp.meta.test.utils

import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.utils.{urlDecode, urlEncode}

class UrlEncodeDecodeTests extends AnyFunSuite{

	def oldUrlEncode(s: String): String = new java.net.URI(null, null, "/" + s, null).toASCIIString.substring(1)

	private val testStrings = Seq("ÄöÅ", "вася/пупкин", "a b", "+a=b%20", "_ # _", """{"a": 1, "b": "bla+bla%"}""")

	test("encoding/decoding round trips"){
		testStrings.foreach{s =>
			assert(s === urlDecode(urlEncode(s)))
		}
	}

	test("old and new urlEncode are equivalent, except on strings with slashes"){
		testStrings.filter(!_.contains('/')).foreach{s =>
			assert(urlEncode(s) === oldUrlEncode(s))
		}
	}
}
