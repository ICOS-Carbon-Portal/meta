package se.lu.nateko.cp.meta.services.sparql.index

import java.time.Instant
import HierarchicalBitmap._

//TODO Write tests, diagnoze amount of values on the leaf nodes in real-case scenarios
/**
 * Factory for HierarchivalBitmap[Long] suitable for representing file sizes in bytes
*/
object FileSizeHierarchicalBitmap{

	val SpilloverThreshold = 513

	def getCoordinate(key: Long, depth: Int): Coord = if(depth <= 0 || depth > 4 || key <= 0) 0 else {
		val shift = depth match{
			case 1 => 10
			case 2 => 7
			case 3 => 4
			case 4 => 0
		}
		((750 * Math.log(key.toDouble)).toInt >> shift).toShort
	}

	def apply(sizeLookup: Int => Long): HierarchicalBitmap[Long] = {

		implicit val geo = new Geo[Long]{

			val spilloverThreshold: Int = SpilloverThreshold

			def keyLookup(value: Int): Long = sizeLookup(value)

			def coordinate(key: Long, depth: Int): Coord = getCoordinate(key, depth)
		}

		new HierarchicalBitmap[Long](0)
	}

}
