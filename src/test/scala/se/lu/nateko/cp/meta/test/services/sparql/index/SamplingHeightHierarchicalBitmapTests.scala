package se.lu.nateko.cp.meta.test.services.sparql.index

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.services.sparql.index.SamplingHeightHierarchicalBitmap

class SamplingHeightHierarchicalBitmapTests extends FunSpec{

	describe("getCoordinate"){
		import SamplingHeightHierarchicalBitmap.getCoordinate

		it("numbers representable with short values are given that value at depth 1"){
			val floats = Array(-3000.5f, -12.7f, 0f, 2f, 100.4f, 2999.9f, 3000.1f)
			for(float <- floats){
				val expected = (float * 10).toShort
				assert(getCoordinate(float, 1) === expected)
			}
		}

		it("close numbers are distinguished on higher depths"){
			assert(getCoordinate(10.11f, 1) === getCoordinate(10.12f, 1))
			assert(getCoordinate(10.11f, 2) < getCoordinate(10.12f, 2))
			assert(getCoordinate(10.111f, 2) === getCoordinate(10.112f, 2))
			assert(getCoordinate(10.111f, 3) < getCoordinate(10.112f, 3))
		}

	}
}
