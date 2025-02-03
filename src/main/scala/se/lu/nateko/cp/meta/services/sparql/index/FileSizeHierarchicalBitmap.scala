package se.lu.nateko.cp.meta.services.sparql.index

import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap
import se.lu.nateko.cp.meta.services.sparql.magic.index.ObjEntry

import java.time.Instant
import scala.collection.IndexedSeq

/**
 * Factory for HierarchivalBitmap[Long] suitable for representing file sizes in bytes
*/
object FileSizeHierarchicalBitmap:
	import HierarchicalBitmap.*

	val SpilloverThreshold = 513

	private val logFactor = Long.MaxValue / Math.log(Long.MaxValue)

	def getCoordinate(key: Long, depth: Int): Coord = if(depth <= 0 || depth > 16 || key <= 0) 0 else {
		val logScaled = (logFactor * Math.log(key.toDouble)).toLong
		val shift = (16 - depth) * 4
		((logScaled & (0xfL << shift)) >> shift).toShort
	}

	def apply(objs: IndexedSeq[ObjEntry]): HierarchicalBitmap[Long] = {
		given Geo[Long] = LongGeo(objs)
		new HierarchicalBitmap[Long](0, None)
	}

	class LongGeo(objs: IndexedSeq[ObjEntry]) extends Geo[Long]:
		val spilloverThreshold: Int = SpilloverThreshold
		def keyLookup(value: Int): Long = objs(value).size
		def coordinate(key: Long, depth: Int): Coord = getCoordinate(key, depth)
