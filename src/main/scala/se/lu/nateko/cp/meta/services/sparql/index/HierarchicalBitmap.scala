package se.lu.nateko.cp.meta.services.sparql.index

import scala.collection.mutable.HashMap
import scala.collection.JavaConverters.asScalaIteratorConverter
import org.roaringbitmap.buffer.MutableRoaringBitmap
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import HierarchicalBitmap._
import java.{util => ju}

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

	private val values = emptyBitmap
	private[this] var n = 0
	private[this] var children: HashMap[Coord, HierarchicalBitmap[K]] = null //to avoid empty map creation in leaf nodes

	def add(key: K, value: Int): Unit = {
		if(depth > 0) values.add(value)

		n += 1

		if(children == null && (depth == 0 || spilledOver)) {
			children = HashMap.empty[Coord, HierarchicalBitmap[K]]
			values.forEach{v => addToChild(geo.keyLookup(v), v)}
		}

		if(children != null) addToChild(key, value) //hasSpilledOver
	}

	private def spilledOver: Boolean = (n >= geo.spilloverThreshold) && {
		//TODO Get rid of this block
		val iter = values.iterator()
		val firstKey = geo.keyLookup(iter.next())
		var seenDifferentKeys: Boolean = false
		while(iter.hasNext() && !seenDifferentKeys){
			val nextKey = geo.keyLookup(iter.next())
			seenDifferentKeys = ord.compare(firstKey, nextKey) != 0
		}
		seenDifferentKeys
	}

	private def addToChild(key: K, value: Int): Unit = {
		val coord = nextLevel(key)
		val child = children.getOrElseUpdate(coord, new HierarchicalBitmap[K](depth + 1))
		child.add(key, value)
	}


	def iterateSorted(filter: Option[ImmutableRoaringBitmap] = None, offset: Int = 0, sortDescending: Boolean = false): Iterator[Int] = {

		val valComp: ju.Comparator[Int] = {
			val keyOrd = if(sortDescending) ord.reverse else ord
			Ordering.by(geo.keyLookup)(keyOrd)
		}

		val coordOrd: Ordering[Coord] = {
			val ascending = implicitly[Ordering[Coord]]
			if(sortDescending) ascending.reverse else ascending
		}

		implicit val iter = new IterationInstruction(filter, valComp, coordOrd)

		innerIterate(offset).fold(_ => Iterator.empty, identity)
	}

	private def innerIterate(offset: Int)(implicit iter: IterationInstruction): OffsetOrResult = if(children == null) {

		val filtered = iter.filter.fold(values){
			ImmutableRoaringBitmap.and(values, _)
		}

		val eagerlyKnownAmount: Option[Int] = if(iter.filter.isEmpty) Some(n) else None

		def sortedIter(knownSize: Option[Int]): Iterator[Int] = {
			val list = knownSize.fold(new ju.ArrayList[Int])(s => new ju.ArrayList[Int](s))
			filtered.forEach(i => {list.add(i);()})
			list.sort(iter.valComp)
			list.iterator.asScala
		}

		if(offset > 0){
			val amount = eagerlyKnownAmount.getOrElse(filtered.getCardinality)
			if(amount <= offset)
				Left(offset - amount)
			else
				Right(sortedIter(Some(amount)).drop(offset))
		} else
			Right(sortedIter(eagerlyKnownAmount))

	} else {
		val bitmapIter: Iterator[HierarchicalBitmap[K]] = children.toSeq.sortBy(_._1)(iter.coordOrd).iterator.map(_._2)
		if(offset <= 0)
			Right(bitmapIter.flatMap(_.innerIterate(0).fold(_ => Iterator.empty, identity)))
		else
			bitmapIter.foldLeft[OffsetOrResult](Left(offset))(
				(acc, bm) => acc match {
					case Left(offset) => bm.innerIterate(offset)
					case Right(iterSoFar) => bm.innerIterate(0).map(iterSoFar ++ _)
				}
			)
	}



	def filter(req: FilterRequest[K]): ImmutableRoaringBitmap = {

		def inner(borderFilter: Coord => Boolean, wholeChildFilter: Coord => Boolean) = MutableRoaringBitmap.or(
			children.collect{
				case (coord, bm) if wholeChildFilter(coord) => bm.values
				case (coord, bm) if borderFilter(coord) => bm.filter(req)
			}.toSeq :_*
		)

		if(children == null){
			if(n >= geo.spilloverThreshold){
				//should have spilled over but did not, therefore all keys are identical
				if(values.isEmpty) values else {
					val theOnlyKey = geo.keyLookup(values.first)
					if(req.filter(theOnlyKey)) values
					else emptyBitmap
				}
			} else {
				val res = emptyBitmap
				values.forEach(v => {
					val key = geo.keyLookup(v)
					if(req.filter(key)) res.add(v)
				})
				res
			}
		} else req match{
			case EqualsFilter(key) =>
				val eqCoord = nextLevel(key)
				inner(eqCoord == _, _ => false)

			case MinFilter(min, _) =>
				val minCoord = nextLevel(min)
				inner(minCoord == _, minCoord < _)

			case MaxFilter(max, _) =>
				val maxCoord = nextLevel(max)
				inner(maxCoord == _, maxCoord > _)

			case IntervalFilter(from, to) =>
				val minC = nextLevel(from.min)
				val maxC = nextLevel(to.max)
				inner(c => c == minC || c == maxC, c => c > minC && c < maxC)
		}
	}

	private def nextLevel(key: K): Coord = geo.coordinate(key, depth + 1)
	private def emptyBitmap = MutableRoaringBitmap.bitmapOf()
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
	case class EqualsFilter[K](key: K)(implicit ord: Ordering[K]) extends FilterRequest[K]{
		val filter = ord.equiv(key, _)
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

	private class IterationInstruction(
		val filter: Option[ImmutableRoaringBitmap],
		val valComp: ju.Comparator[Int],
		val coordOrd: Ordering[Coord]
	)

	private type OffsetOrResult = Either[Int, Iterator[Int]]

	// private def time[T](comp: => T): T = {
	// 	val start = System.nanoTime
	// 	val res = comp
	// 	val us = (System.nanoTime - start) / 1000
	// 	println(s"Elapsed $us us")
	// 	res
	// }

}
