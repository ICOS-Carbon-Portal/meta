package se.lu.nateko.cp.meta.services.sparql.index

import java.time.Instant
import HierarchicalBitmap._

/**
 * Factory for HierarchivalBitmap[Long] suitable for representing file sizes in bytes
*/
object FileSizeHierarchicalBitmap{

	val SpilloverThreshold = 513

	private val logFactor = Long.MaxValue / Math.log(Long.MaxValue)

	def getCoordinate(key: Long, depth: Int): Coord = if(depth <= 0 || depth > 16 || key <= 0) 0 else {
		val logScaled = (logFactor * Math.log(key.toDouble)).toLong
		val shift = (16 - depth) * 4
		((logScaled & (0xfL << shift)) >> shift).toShort
	}

	def apply(sizeLookup: Int => Long): HierarchicalBitmap[Long] = {

		implicit val geo = new Geo[Long]{

			val spilloverThreshold: Int = SpilloverThreshold

			def keyLookup(value: Int): Long = sizeLookup(value)

			def coordinate(key: Long, depth: Int): Coord = getCoordinate(key, depth)
		}

		new HierarchicalBitmap[Long](0, None)
	}

}
