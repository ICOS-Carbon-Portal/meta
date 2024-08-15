package se.lu.nateko.cp.meta.test.services.metaexport

import se.lu.nateko.cp.meta.services.metaexport.DoiGeoCovConverter
import org.scalatest.funspec.AnyFunSpec

class DoiGeoCovConverterTests extends AnyFunSpec:
	describe("mergeLabels"):
		it("reduces numerical variable indices, sorts the labels"):
			val labels = List("SW_IN_3_1_1 / SW_IN_3_1_1 / SW_IN_3_1_1", "P_2_1_1", "CD-Ygb", "CH-Dav")

			val merged = DoiGeoCovConverter.mergeLabels(labels)
			val expected = Some("CD-Ygb, CH-Dav, SW_IN_n_n_n, P_n_n_n")

			assert(merged == expected)