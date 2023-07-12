package se.lu.nateko.cp.meta.services.sparql.index

import java.time.Instant
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData

/**
 * Factory for HierarchivalBitmap[Long] suitable for representing file sizes in bytes
*/
object FileSizeHierarchicalBitmap{
	import HierarchicalBitmap.*

	val SpilloverThreshold = 513

	private val logFactor = Long.MaxValue / Math.log(Long.MaxValue)

	def getCoordinate(key: Long, depth: Int): Coord = if(depth <= 0 || depth > 16 || key <= 0) 0 else {
		val logScaled = (logFactor * Math.log(key.toDouble)).toLong
		val shift = (16 - depth) * 4
		((logScaled & (0xfL << shift)) >> shift).toShort
	}

	def apply(idx: IndexData): HierarchicalBitmap[Long] = {
		given Geo[Long] = LongGeo(idx)
		new HierarchicalBitmap[Long](0, None)
	}

	class LongGeo(idx: IndexData) extends Geo[Long]{
		private def this() = this(null)//for Kryo deserialization
		val spilloverThreshold: Int = SpilloverThreshold
		def keyLookup(value: Int): Long = idx.objs(value).size
		def coordinate(key: Long, depth: Int): Coord = getCoordinate(key, depth)
	}

}
