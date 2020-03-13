package se.lu.nateko.cp.meta.test.services.sparql.index
import java.time.Instant
import scala.io.Source
import se.lu.nateko.cp.meta.services.sparql.index.DatetimeHierarchicalBitmap
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap._
import scala.jdk.CollectionConverters.IteratorHasAsScala

class LargeScaleDatetimeTest extends AnyFunSpec{
	import LargeScaleDatetimeTest.parseTs

	ignore("Filtering on real-life set of data start times (to use, run startTimes.sh in src/test/resources)"){
		lazy val (arr, bm) = LargeScaleDatetimeTest.makeBm

		it("Interval filtering results include expected CH-Dav raw EC data"){
			val min = MinFilter[Long](parseTs("2019-08-24T12:00:00Z"), false)
			val max = MaxFilter[Long](parseTs("2019-08-28T12:00:00Z"), false)
			val filter = bm.filter(IntervalFilter(min, max))
			val filterFnames = filter.iterator().asScala.map(i => arr(i).fileName).toSet
			//val fnames = bm.iterateSorted(Some(filter)).map(i => arr(i).fileName).toSet
			val expectedFnames = Set(25, 26, 27, 28).map(i => s"CH-Dav_EC_201908${i}_L01_F05.zip")
			assert(expectedFnames.diff(filterFnames).isEmpty)
		}
	}
}

object LargeScaleDatetimeTest{

	class Entry(val fileName: String, val dt: Long)

	def getEntries: Iterator[Entry] = Source.fromResource("startTimes.csv").getLines().drop(1).map{line =>
		val rows = line.split(',')
		new Entry(rows(2), parseTs(rows(1)))
	}

	def parseTs(ts: String): Long = Instant.parse(ts).toEpochMilli

	def makeBm = {
		val arr = getEntries.toArray
		val bm = DatetimeHierarchicalBitmap(i => arr(i).dt)
		arr.indices.foreach{i => bm.add(arr(i).dt, i)}
		arr -> bm
	}
}
