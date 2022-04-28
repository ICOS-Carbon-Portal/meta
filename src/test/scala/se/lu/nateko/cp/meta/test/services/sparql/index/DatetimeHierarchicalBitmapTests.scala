package se.lu.nateko.cp.meta.test.services.sparql.index

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sparql.index.*
import scala.jdk.CollectionConverters.IteratorHasAsScala

import HierarchicalBitmap.*
import scala.util.Random
import java.time.Instant

class DatetimeHierarchicalBitmapTests extends AnyFunSpec{
	val Min = toMillis("2010-01-01T00:00:00Z")
	val Max = toMillis("2020-01-01T00:00:00Z")

	private val EnableTrace = false

	def toMillis(s: String): Long = Instant.parse(s).toEpochMilli

	val millis = IndexedSeq(
		"2015-02-02T10:30:00Z", "2019-09-01T00:00:00Z", "2015-01-01T00:00:00Z", "2017-01-01T00:00:00Z", "2015-02-02T10:00:00Z"
	).map(toMillis)

	val bm = initBm(millis)

	def initBm(longs: IndexedSeq[Long]): HierarchicalBitmap[Long] = {
		val bm = DatetimeHierarchicalBitmap(longs.apply)
		longs.indices.foreach(i => bm.add(longs(i), i))
		bm
	}

	def initRandom(size: Int): (HierarchicalBitmap[Long], Array[Long]) = {
		val longs = initArray(size)
		val bm = initBm(longs.toIndexedSeq)
		(bm, longs)
	}

	def initArray(size: Int): Array[Long] = {
		val rnd = new Random(333)
		Array.fill(size)(Min + rnd.nextLong(Max-Min))
	}


	describe("hierarchy-coordinate calculation"){
		import DatetimeHierarchicalBitmap.getCoordinate

		describe("monotonicity (larger long gives larger or equal coordinate)"){

			def testForDepth(arr: Array[Long], depth: Int): Unit = {
				val sortedLongs = arr.sorted
				val reSorted = sortedLongs.sortBy(getCoordinate(_, depth))
				assert(reSorted === sortedLongs)
			}
			it("is monotonic on depth 1"){
				testForDepth(initArray(1000), 1)
			}

			it("is monotonic on depth 2"){
				for((_, longs) <- initArray(1000).groupBy(getCoordinate(_, 1))){
					testForDepth(longs, 2)
				}
			}
		}

		it("has expected spread of coordinate values on depth 1"){
			val coords = initArray(1000).map(getCoordinate(_, 1)).distinct
			assert(coords.length == 5)
		}
	}

	describe("filtering"){

		def testFilter(req: FilterRequest[Long], expected: Seq[Int]) = {
			assert(bm.filter(req).iterator().asScala.toSeq == expected)
		}

		it("EqualsFilter works"){
			for(i <- millis.indices){
				testFilter(EqualsFilter(millis(i)), Seq(i))
			}
			testFilter(EqualsFilter(12345L), Seq())
		}

		it("inclusive MinFilter works"){
			for(minv <- millis){
				val expected = millis.indices.filter(millis(_) >= minv)
				testFilter(MinFilter(minv, inclusive = true), expected)
			}
			testFilter(MinFilter(toMillis("2016-01-01T00:00:00Z"), inclusive = true), Seq(1, 3))
		}

		it("exclusive MaxFilter works"){
			for(maxv <- millis){
				val expected = millis.indices.filter(millis(_) < maxv)
				testFilter(MaxFilter(maxv, inclusive = false), expected)
			}
			testFilter(MaxFilter(toMillis("2016-01-01T00:00:00Z"), inclusive = false), Seq(0, 2, 4))
		}

		it("large bitmap, interval filter"){
			val (bm, _) = initRandom(1000)
			val min = MinFilter(toMillis("2012-01-01T00:00:00Z"), false)
			val max = MaxFilter(toMillis("2017-01-01T00:00:00Z"), false)
			val filteredSize = bm.filter(IntervalFilter(min, max)).getCardinality
			assert(filteredSize > 450 && filteredSize < 550)
		}
	}

	describe("iterateSorting"){

		it("small index, many variably-filtered iterations, with/without offset, ascending/descending sort"){
			val fullRange = (Min - 1) +: millis :+ (Max + 1)
			for(
				minvalue <- fullRange;
				maxvalue <- fullRange if(minvalue <= maxvalue);
				filter = bm.filter(IntervalFilter(MinFilter(minvalue, true), MaxFilter(maxvalue, true)));
				offset <- Array(0, 3, 8);
				desc <- Array(false, true)
			){
				val sortedRes = bm.iterateSorted(Some(filter), offset = offset, sortDescending = desc).toIndexedSeq

				val longOrd = implicitly[Ordering[Long]]

				val expected = millis.indices
					.filter(i => minvalue <= millis(i) && maxvalue >= millis(i))
					.sortBy(millis.apply)(if(desc) longOrd.reverse else longOrd)
					.drop(offset)

				assert(sortedRes == expected)
			}
		}


		it("large random index, unfiltered iteration, with offset"){
			val n = 1000; val offset = n / 2; val limit = 5

			val (largeBm, manyStrings) = initRandom(n)

			val iter = time(s"iterateSorted(offset = $offset)")(largeBm.iterateSorted(offset = offset))

			val res = time(s"iterator.take($limit).toIndexedSeq (size $n , offset $offset)"){
				iter.take(limit).toIndexedSeq
			}
			val expected = manyStrings.indices.sortBy(manyStrings.apply).drop(offset).take(limit)

			assert(res == expected)
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
