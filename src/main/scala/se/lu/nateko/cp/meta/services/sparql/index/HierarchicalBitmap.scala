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
	private[this] var children: HashMap[Coord, HierarchicalBitmap[K]] = null
	private[this] var firstKey: Option[K] = None
	private[this] var seenDifferentKeys: Boolean = false

	def all: ImmutableRoaringBitmap = values

	/**
	 * Adds the value. The value must not be already present.
	 * @param key must be the same as what {{{geo.keyLookup(value)}}} would return.
	 * @throws java.lang.AssertionError if the value is already present.
	*/
	def add(key: K, value: Int): Unit = {

		assert(!values.contains(value), s"value $value is already contained in a bitmap at depth $depth")
		values.add(value)
		n += 1
		if(children != null) addToChild(key, value)

		if(!seenDifferentKeys) assessDiversityOfKeys(key)

		if(children == null && seenDifferentKeys && (n >= geo.spilloverThreshold)) {
			children = HashMap.empty
			values.forEach{v => addToChild(geo.keyLookup(v), v)}
		}

	}

	/**
	 * Removes the value, returning true if value was present and false otherwise.
	 * @param key must be the same as the one supplied when the value was added.
	 * @throws java.lang.AssertionError if the value was present but not inserted with the key supplied.
	 */
	def remove(key: K, value: Int): Boolean = {
		val wasPresentInSelf = values.contains(value)
		values.remove(value)
		if(wasPresentInSelf) n -= 1

		if(children == null) wasPresentInSelf else {
			val coord = nextLevel(key)
			val wasPresentInChildren = children.get(coord).map(_.remove(key, value)).getOrElse(false)

			assert(wasPresentInChildren == wasPresentInSelf, "Inconsistency in Hierarchical bitmap!")

			wasPresentInSelf // `|| wasPresentInChildren` is not needed due to the assertion above
		}
	}

	private def assessDiversityOfKeys(key: K): Unit = firstKey match{
		case None =>       firstKey = Some(key)
		case Some(fKey) => seenDifferentKeys = nextLevel(key) != nextLevel(fKey)
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

		open(innerIterate(offset))
	}

	private def innerIterate(offset: Int)(implicit iter: IterationInstruction): OffsetOrResult =
		if(offset >= n){
			val amount = iter.filter.fold(n){filter =>
				ImmutableRoaringBitmap.andCardinality(values, filter)
			}
			Left(offset - amount)

		} else if(children == null) {

			val (filtered, amount) = iter.filter.fold(values -> n){filterBm =>
				val filtered = ImmutableRoaringBitmap.and(values, filterBm)
				filtered -> filtered.getCardinality
			}

			if(offset >= amount && offset > 0) Left(offset - amount)
			else if(amount > 0) Right{
				val res =
					if(seenDifferentKeys && amount > 1){
						val list = new ju.ArrayList[Int](amount)
						filtered.forEach(i => {list.add(i);()})
						list.sort(iter.valComp)
						list.iterator.asScala
					} else
						filtered.iterator.asScala.map(_.intValue)

				res.drop(offset)
			} else Right(Iterator.empty)

		} else {
			val childrenIter: Iterator[HierarchicalBitmap[K]] = children.toSeq.sortBy(_._1)(iter.coordOrd).iterator.map(_._2)

			if(offset <= 0)
				Right(childrenIter.flatMap(childIter => open(childIter.innerIterate(0))))
			else
				childrenIter.foldLeft[OffsetOrResult](Left(offset))(
					(acc, bm) => acc match {
						case Left(offset)     => bm.innerIterate(offset)
						case Right(iterSoFar) => Right(iterSoFar ++ open(bm.innerIterate(0)))
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
			if(!seenDifferentKeys){
				if(values.isEmpty) values else {
					val theOnlyKey = geo.keyLookup(values.first)
					if(filterKey(theOnlyKey, req)) values
					else emptyBitmap
				}
			} else {
				val filtered = emptyBitmap
				values.forEach(v => {
					val key = geo.keyLookup(v)
					if(filterKey(key, req)) filtered.add(v)
				})
				filtered
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

	def optimizeAndTrim(): Unit = {
		values.runOptimize()
		values.trim()
		if(children != null) children.valuesIterator.foreach(_.optimizeAndTrim())
	}

	def filterKey(key: K, filter: FilterRequest[K]): Boolean = filter match{
		case EqualsFilter(fkey) => ord.equiv(key, fkey)
		case MinFilter(min, inclusive) => if(inclusive) ord.lteq(min, key) else ord.lt(min, key)
		case MaxFilter(max, inclusive) => if(inclusive) ord.gteq(max, key) else ord.gt(max, key)
		case IntervalFilter(from, to) => filterKey(key, from) && filterKey(key, to)
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

	sealed trait FilterRequest[K]
	case class EqualsFilter[K](key: K) extends FilterRequest[K]
	case class MinFilter[K](min: K, inclusive: Boolean) extends FilterRequest[K]
	case class MaxFilter[K](max: K, inclusive: Boolean) extends FilterRequest[K]
	case class IntervalFilter[K](from: MinFilter[K], to: MaxFilter[K]) extends FilterRequest[K]

	private class IterationInstruction(
		val filter: Option[ImmutableRoaringBitmap],
		val valComp: ju.Comparator[Int],
		val coordOrd: Ordering[Coord]
	)

	private type OffsetOrResult = Either[Int, Iterator[Int]]
	private def open(res: OffsetOrResult): Iterator[Int] = res.fold(_ => Iterator.empty, identity)

	// private def time[T](comp: => T): T = {
	// 	val start = System.nanoTime
	// 	val res = comp
	// 	val us = (System.nanoTime - start) / 1000
	// 	println(s"Elapsed $us us")
	// 	res
	// }

}
