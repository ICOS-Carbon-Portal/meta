package se.lu.nateko.cp.meta.test.services.sparql.index

import org.scalatest.FunSpec
import se.lu.nateko.cp.meta.services.sparql.index._
import scala.collection.JavaConverters.asScalaIteratorConverter

import HierarchicalBitmap._
import StringHierarchicalBitmap.Ord
import scala.util.Random

class StringHierarchicalBitmapTests extends FunSpec{

	private[this] val EnableTrace = false

	def initBm(strings: IndexedSeq[String]): HierarchicalBitmap[String] = {
		val bm = StringHierarchicalBitmap(strings.apply)
		strings.indices.foreach(i => bm.add(strings(i), i))
		bm
	}

	def initRandom(size: Int): (HierarchicalBitmap[String], Array[String]) = {
		val rnd = new Random(333)
		val strings = time(s"init array (size = $size)")(Array.fill(size)(rnd.alphanumeric.take(6).mkString))
		val bm = time(s"initBm(array.size = $size)")(initBm(strings))
		(bm, strings)
	}

	def testFilter(req: FilterRequest[String], expected: Seq[Int])(implicit bm: HierarchicalBitmap[String]) = {
		assert(bm.filter(req).iterator().asScala.toSeq == expected)
	}

	describe("string ordering"){

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
		def sortStringInds(s: Array[String]) = s.indices.sortBy(s.apply)(Ord)
		val bm = initBm(strings)

		it("small index, unfiltered iteration, without offset"){
			assert(bm.iterateSorted().toSeq == sortStringInds(strings))
		}

		it("small index, unfiltered iteration, with offset"){
			val offset = 5
			assert(bm.iterateSorted(offset = offset).toSeq == sortStringInds(strings).drop(offset))
		}

		it("small index, many variably-filtered iterations, with/without offset, ascending/descending sort"){
			val fullRange = "aaaa" +: strings :+ "zzz"
			for(
				minvalue <- fullRange;
				maxvalue <- fullRange if(Ord.lteq(minvalue, maxvalue));
				filter = bm.filter(IntervalFilter(MinFilter(minvalue, true), MaxFilter(maxvalue, true)));
				offset <- Array(0, 3, 5, 8, 100);
				desc <- Array(false, true)
			){
				val sortedRes = bm.iterateSorted(Some(filter), offset = offset, sortDescending = desc).toIndexedSeq

				val expected = strings.indices
					.filter(i => Ord.lteq(minvalue, strings(i)) && Ord.gteq(maxvalue, strings(i)))
					.sortBy(strings.apply)(if(desc) Ord.reverse else Ord)
					.drop(offset)

				assert(sortedRes == expected)
			}
		}

		it("large random index, unfiltered iteration, without offset"){
			val (largeBm, manyStrings) = initRandom(1000)
			val result = largeBm.iterateSorted().toIndexedSeq
			assert(result.toSeq == sortStringInds(manyStrings))
		}

		it("large random index, unfiltered iteration, with offset"){
			val n = 1000; val offset = n / 2; val limit = 100

			val (largeBm, manyStrings) = time(s"initRandom($n)")(initRandom(n))

			val iter = time(s"iterateSorted(offset = $offset)")(largeBm.iterateSorted(offset = offset))

			val res = time(s"iterator.take($limit).toIndexedSeq (size $n , offset $offset)"){
				iter.take(limit).toIndexedSeq
			}
			assert(res == sortStringInds(manyStrings).drop(offset).take(limit))
		}

	}

	private def time[T](what: String)(comp: => T): T = {
		val start = System.currentTimeMillis()
		val res = comp
		val elapsed = System.currentTimeMillis() - start
		if(EnableTrace) println(s"Elapsed on $what : $elapsed ms")
		res
	}
}
