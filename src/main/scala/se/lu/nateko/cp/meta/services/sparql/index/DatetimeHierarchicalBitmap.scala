package se.lu.nateko.cp.meta.services.sparql.index

import java.time.Instant
import HierarchicalBitmap._

/**
 * Factory for HierarchivalBitmap[Long] suitable for representing java.time.Instant keys
 * (converted to milliseconds since epoch). Internal constants for hierarchy-coordinate calculation
 * algorithm are chosen so that the algorithm works correctly only for years from approximately 1420 AD to 2520 AD
*/
object DatetimeHierarchicalBitmap{

	val SpilloverThreshold = 513

	def getCoordinate(key: Long, depth: Int): Coord = if(depth <= 0 || depth > 10) 0 else {
		val vabs = Math.abs(key)
		val mask = if(depth == 1) 0xffl else 0xfl
		val shift = (10 - depth) * 4
		(key.signum * ((vabs & (mask << shift)) >> shift)).toShort
	}

	def apply(millisLookup: Int => Long): HierarchicalBitmap[Long] = {

		implicit val geo = new Geo[Long]{

			val spilloverThreshold: Int = SpilloverThreshold

			def keyLookup(value: Int): Long = millisLookup(value)

			def coordinate(key: Long, depth: Int): Coord = getCoordinate(key, depth)
		}

		new HierarchicalBitmap[Long](0)
	}

}
