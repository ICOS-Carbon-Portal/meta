package se.lu.nateko.cp.meta.core.tests.data

import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.meta.core.CommonJsonSupport.sealedTraitTypeclassLookup
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import spray.json.JsonFormat

class MacroJsonTests extends AnyFunSuite:

	test(s"geofeature macro test"){
		val tcLookup = sealedTraitTypeclassLookup[GeoFeature, JsonFormat]
		println(tcLookup)
	}
