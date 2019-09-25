package se.lu.nateko.cp.meta.test.services.sparql.index

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.services.sparql.index._
import scala.collection.JavaConverters.asScalaIteratorConverter

import HierarchicalBitmap._
import scala.util.Random

class StringHierarchicalBitmapTests extends FunSpec{

	def initBm(strings: IndexedSeq[String]): HierarchicalBitmap[String] = {
		val bm = StringHierarchicalBitmap(strings.apply)
		strings.indices.foreach(i => bm.add(strings(i), i))
		bm
	}

	def testFilter(req: FilterRequest[String], expected: Seq[Int])(implicit bm: HierarchicalBitmap[String]) = {
		assert(bm.filter(req).iterator().asScala.toSeq == expected)
	}

	describe("string ordering"){
		import StringHierarchicalBitmap.Ord

		it("works as expected"){
			assert(Ord.compare("aardvark", "aaaa") > 0)
			assert(Ord.compare("aaaa", "aardvark") < 0)
			assert(Ord.compare("", "") == 0)
			assert(Ord.compare("a", "aa") < 0)
			assert(Ord.compare("Örkelljunga", "Olofström") > 0)
		}
	}

	describe("filtering"){

		val strings = Array("zulu", "mememe", "bebebe", "aardvark")
		implicit val bm = initBm(strings)

		it("EqualsFilter works"){
			testFilter(EqualsFilter("bababa"), Seq())
			testFilter(EqualsFilter("mememe"), Seq(1))
		}

		it("inclusive MinFilter works"){
			testFilter(MinFilter("bababa", inclusive = true), Seq(0, 1, 2))
			testFilter(MinFilter("mememe", inclusive = true), Seq(0, 1))
		}

		it("exclusive MinFilter works"){
			testFilter(MinFilter("aardvark", inclusive = false), Seq(0, 1, 2))
			testFilter(MinFilter("xena", inclusive = false), Seq(0))
		}

		it("inclusive MaxFilter works"){
			testFilter(MaxFilter("bababa", inclusive = true), Seq(3))
			testFilter(MaxFilter("mememe", inclusive = true), Seq(1, 2, 3))
		}

		it("exclusive MaxFilter works"){
			testFilter(MaxFilter("aardvark", inclusive = false), Seq())
			testFilter(MaxFilter("xena", inclusive = false), Seq(1, 2, 3))
		}

		it("IntervalFilter works"){
			val filter1 = IntervalFilter(MinFilter("bebebe", true), MaxFilter("mememe", true))
			testFilter(filter1, Seq(1, 2))
			val filter2 = IntervalFilter(MinFilter("cc", true), MaxFilter("oo", true))
			testFilter(filter2, Seq(1))
		}

	}

	describe("indexing large number of random strings"){
		val rnd = new Random(333)
		val n = 1000
		val strings = Array.fill(n)(rnd.nextString(6))
		val bm = initBm(strings)
		val size = bm.filter(MinFilter("naaaaa", false)).iterator.asScala.size
		assert(size > n / 2 && size < n * 2 / 3)
	}

	describe("bitmap with large number of identical strings"){
		val kaboom = "kaboom!"
		val strings = Array.fill(StringHierarchicalBitmap.SpilloverThreshold)(kaboom)
		implicit val bm = initBm(strings)
		it("EqualsFilter works"){
			testFilter(EqualsFilter(kaboom), strings.indices)
		}
	}

	describe("iterateSorting"){
		val strings = Array("oops", "zulu", "mememe", "bebebe", "aardvark", "just", "about", "any", "kind", "of", "jibberish")
		def sortStringInds(s: Array[String]) = s.indices.sortBy(s.apply)(StringHierarchicalBitmap.Ord)
		val bm = initBm(strings)

		it("unfiltered iteration without offset works"){
			assert(bm.iterateSorted().toSeq == sortStringInds(strings))
		}

		it("unfiltered iteration with offset works"){
			val offset = 5
			assert(bm.iterateSorted(offset = offset).toSeq == sortStringInds(strings).drop(offset))
		}

		it("unfiltered iteration without offset over large bitmap index works"){
			val rnd = new Random(333)
			val n = 1000
			val manyStrings = Array.fill(n)(rnd.alphanumeric.take(6).mkString)
			val largeBm = initBm(manyStrings)
			val result = largeBm.iterateSorted().toIndexedSeq
			assert(result.toSeq == sortStringInds(manyStrings))
		}

	}

	// private def time[T](comp: => T): T = {
	// 	val start = System.currentTimeMillis()
	// 	val res = comp
	// 	println(System.currentTimeMillis() - start)
	// 	res
	// }
}
