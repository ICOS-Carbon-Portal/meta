package se.lu.nateko.cp.meta.services.sparql.index

import java.time.Instant
import HierarchicalBitmap.*
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData

/**
 * Factory for HierarchivalBitmap[Long] suitable for representing java.time.Instant keys
 * (converted to milliseconds since epoch). Internal constants for hierarchy-coordinate calculation
 * algorithm are chosen so that the algorithm works correctly only for years from approximately 1420 AD to 2520 AD
*/
object DatetimeHierarchicalBitmap{

	val SpilloverThreshold = 513

	def getCoordinate(key: Long, depth: Int): Coord = if(depth <= 0 || depth > 10) 0 else {
		val vabs = Math.abs(key)
		val mask = if(depth == 1) 0xffL else 0xfL
		val shift = (10 - depth) * 4
		(key.sign * ((vabs & (mask << shift)) >> shift)).toShort
	}

	def apply(millisLookup: Int => Long): HierarchicalBitmap[Long] = {
		given Geo[Long] = LongGeo(millisLookup)
		new HierarchicalBitmap[Long](0, None)
	}

	def dataStart(idx: IndexData) = apply(value => idx.objs(value).dataStart)
	def dataEnd(idx: IndexData) = apply(value => idx.objs(value).dataEnd)
	def submStart(idx: IndexData) = apply(value => idx.objs(value).submissionStart)
	def submEnd(idx: IndexData) = apply(value => idx.objs(value).submissionEnd)

	class LongGeo(lookup: Int => Long) extends Geo[Long]{
		private def this() = this(null)//for Kryo deserialization
		val spilloverThreshold: Int = SpilloverThreshold
		def keyLookup(value: Int): Long = lookup(value)
		def coordinate(key: Long, depth: Int): Coord = getCoordinate(key, depth)
	}
}
