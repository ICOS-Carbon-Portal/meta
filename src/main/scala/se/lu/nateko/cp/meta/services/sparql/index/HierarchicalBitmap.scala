package se.lu.nateko.cp.meta.services.sparql.index

import scala.collection.mutable.HashMap
import org.roaringbitmap.buffer.MutableRoaringBitmap

trait HierarchyGeo[K]{
	/** depth zero always returns zero */
	def coordinate(depth: Int, key: K): Int
	def reverseLookup(value: Int): K
	def spilloverThreshold: Int
}

/**
 * Assumptions:
 * - only adding, never removing;
 * - hierarchy "coordinate" calculation is consistent with ordering (larger coord means strictly larger key)
 * - spillover threshold is reasonably sized for very fast sort
*/
class HierarchicalBitmap[K](depth: Int)(implicit geo: HierarchyGeo[K], ord: Ordering[K]){

	private[this] var n = 0
	private[this] var children: HashMap[Int, HierarchicalBitmap[K]] = null //to avoid empty map creation in leaf nodes
	private[this] val values = MutableRoaringBitmap.bitmapOf()

	def add(key: K, value: Int): Unit = {
		if(depth > 0) values.add(value)

		n += 1

		if(n == geo.spilloverThreshold + 1 || depth == 0) {
			children = HashMap.empty[Int, HierarchicalBitmap[K]]
			values.forEach{v => addToChild(geo.reverseLookup(v), v)}
		}

		if(children != null) addToChild(key, value) //hasSpilledOver
	}

	private def addToChild(key: K, value: Int): Unit = {
		val coord = geo.coordinate(depth + 1, key)
		val child = children.getOrElseUpdate(coord, new HierarchicalBitmap[K](depth + 1))
		child.add(key, value)
	}

	def ascendingValues: Iterator[Int] = if(children == null) ??? else ???

}

object HierarchicalBitmap{
}
