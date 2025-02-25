package se.lu.nateko.cp.meta.test.metaflow.cities

import java.nio.file.Paths
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.CountryCode
import se.lu.nateko.cp.meta.metaflow.cities.MidLowCostMetaSource.parseStation
import se.lu.nateko.cp.meta.metaflow.icos.AtcMetaSource.parseFromCsv


class MidLowCostMetaSourceTest extends AnyFunSpec:
	describe("sites table parsing"):
		val sitesCsvFile = Paths.get(getClass.getResource("/midLowCostSites.csv").toURI)
		val CountryCode(de) = "DE" : @unchecked
		import se.lu.nateko.cp.meta.metaflow.cities.MunichConf
		val resV = parseFromCsv(sitesCsvFile)(parseStation(de))
		it("has no errors"):
			assert(resV.errors.isEmpty)
		it("produces results"):
			assert(resV.result.isDefined)
			assert(resV.result.get.size === 66)