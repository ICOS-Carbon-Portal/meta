package se.lu.nateko.cp.meta.core.tests.data

import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.core.data.Orcid

class OrcidTests extends AnyFunSuite{

	test("exctracts Orcids from correct strings, gets the strings back"){
		Seq(
			"https://orcid.org/0000-0002-1825-0097",
			"https://orcid.org/0000-0001-5109-3700",
			"https://orcid.org/0000-0002-1694-233X"
		).foreach{
			case s @ Orcid(orcid) => assert(orcid.id === s)
			case unmatched => fail(s"Could not parse $unmatched as Orcid id")
		}
	}

	test("Orcid extraction fails on wrong strings"){
		Seq(
			"", //empty input
			"https://orcid.org/000-0002-1825-0097", //too few digits
			"https://orcid.org/0000-0001-5109-3701",  //wrong checksum
			"https://orcid.org/0000-0002-1694-233a" //illegal character at the end, even though numericValue('a') = 10 -- correct checksum
		).foreach{s =>
			assert(Orcid.unapply(s).isEmpty)
		}
	}

	test("Equality works as expected"){
		val Orcid(orc1) = "0000-0002-1825-0097"
		val Orcid(orc2) = "0000-0002-1825-0097"
		val Orcid(orc3) = "0000-0002-1694-233X"
		assert(orc1.equals(orc2))
		assert(!orc1.equals(orc3))
		val badNull: Orcid = null
		assert(!orc1.equals(badNull))
	}
}
