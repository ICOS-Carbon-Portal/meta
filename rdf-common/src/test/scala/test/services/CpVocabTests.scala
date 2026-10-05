package se.lu.nateko.cp.meta.test.services

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.CpVocab

class CpVocabTests extends AnyFunSpec:

	describe("getEcoVariableFamily"):
		import CpVocab.{KnownEcoVarFamilies, getEcoVariableFamily}
		it("identifies the var family name for all supported families"):
			KnownEcoVarFamilies.foreach: fam =>
				assert(getEcoVariableFamily(s"${fam}_3_14_1") === Some(fam))

		it("returns None on other variables"):
			Seq("bla_bla_1_1_1", "anyvar", "SW_OUT", "TS_1_1").foreach: other =>
				assert(getEcoVariableFamily(other) === None)
