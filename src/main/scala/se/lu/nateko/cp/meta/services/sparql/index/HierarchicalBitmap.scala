package se.lu.nateko.cp.meta.services.sparql.index

import scala.collection.mutable.HashMap
import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import HierarchicalBitmap._

/**
 * Assumptions:
 * - only adding, never removing;
 * - hierarchy "coordinate" calculation is consistent with ordering (larger coord means strictly larger key)
 * - the key intervals covered by hierarchy "coordinates" are inclusive on the left and exclusive on the right
 * - spillover threshold is reasonably sized for very fast sort
 * - number of coordinate-indices on every depth level is small enough for fast batch-operations on bitmaps
 * - a key may correspond to multiple values, but every value has a single key
*/
class HierarchicalBitmap[K](depth: Int)(implicit geo: Geo[K], ord: Ordering[K]){

	private[this] var n = 0
	private[this] var children: HashMap[Coord, HierarchicalBitmap[K]] = null //to avoid empty map creation in leaf nodes
	private val values = MutableRoaringBitmap.bitmapOf()

	def add(key: K, value: Int): Unit = {
		if(depth > 0) values.add(value)

		n += 1

		//TODO Make sure the case of all values having same key does not result in unconstrained growth of depth
		if(n == geo.spilloverThreshold || depth == 0) {
			children = HashMap.empty[Coord, HierarchicalBitmap[K]]
			values.forEach{v => addToChild(geo.keyLookup(v), v)}
		}

		if(children != null) addToChild(key, value) //hasSpilledOver
	}

	private def addToChild(key: K, value: Int): Unit = {
		val coord = nextLevel(key)
		val child = children.getOrElseUpdate(coord, new HierarchicalBitmap[K](depth + 1))
		child.add(key, value)
	}

	def ascendingValues: Iterator[Int] = if(children == null) ??? else ???


	def filter(req: FilterRequest[K]): ImmutableRoaringBitmap = {

		def inner(borderFilter: Coord => Boolean, wholeChildFilter: Coord => Boolean): ImmutableRoaringBitmap = {
			MutableRoaringBitmap.or(
				children.collect{
					case (coord, bm) if wholeChildFilter(coord) => bm.values
					case (coord, bm) if borderFilter(coord) => bm.filter(req)
				}.toSeq :_*
			)
		}

		def minOrMax(x: K, coordFilter: (Coord, Coord) => Boolean): ImmutableRoaringBitmap = {
			val xCoord = nextLevel(x)
			inner(xCoord == _, coordFilter(xCoord, _))
		}

		if(children == null){
			val res = MutableRoaringBitmap.bitmapOf()

			values.forEach(v => {
				val key = geo.keyLookup(v)
				if(req.filter(key)) res.add(v)
			})

			res
		} else req match{

			case MinFilter(min, _) => minOrMax(min, _ < _)

			case MaxFilter(max, _) => minOrMax(max, _ > _)

			case IntervalFilter(from, to) =>
				val minC = nextLevel(from.min)
				val maxC = nextLevel(to.max)
				inner(c => c == minC || c == maxC, c => c > minC && c < maxC)
		}
	}

	private def nextLevel(key: K): Coord = geo.coordinate(key, depth + 1)
}

object HierarchicalBitmap{
	type Coord = Short
	trait Geo[K]{
		/** depth zero always returns zero */
		def coordinate(key: K, depth: Int): Coord
		def keyLookup(value: Int): K
		def spilloverThreshold: Int
	}
	sealed trait FilterRequest[K]{
		val filter: K => Boolean
	}
	case class MinFilter[K](min: K, inclusive: Boolean)(implicit ord: Ordering[K]) extends FilterRequest[K]{
		val filter = if(inclusive) ord.lteq(min, _) else ord.lt(min, _)
	}
	case class MaxFilter[K](max: K, inclusive: Boolean)(implicit ord: Ordering[K]) extends FilterRequest[K]{
		val filter = if(inclusive) ord.gteq(max, _) else ord.gt(max, _)
	}
	case class IntervalFilter[K](from: MinFilter[K], to: MaxFilter[K]) extends FilterRequest[K]{
		val filter = v => from.filter(v) && to.filter(v)
	}
}
