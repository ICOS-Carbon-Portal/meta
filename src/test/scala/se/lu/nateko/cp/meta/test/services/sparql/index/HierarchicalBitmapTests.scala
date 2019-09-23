package se.lu.nateko.cp.meta.test.services.sparql.index
import org.scalatest.FunSuite
import org.roaringbitmap.buffer.MutableRoaringBitmap

class HierarchicalBitmapTests extends FunSuite{
	test("batch-OR of MutableRoaringBitmap gives empty bitmap for empty list of bitmaps"){
		val or = MutableRoaringBitmap.or()
		assert(or.getCardinality() === 0)
	}
}