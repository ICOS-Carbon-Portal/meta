package se.lu.nateko.cp.meta.core.algo

import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap

import java.io.DataInput
import java.io.DataOutput
import java.io.Serializable
import java.util as ju
import scala.collection.mutable.HashMap
import scala.jdk.CollectionConverters.IteratorHasAsScala

import HierarchicalBitmap.*

/**
 * Assumptions:
 * - only adding, never removing;
 * - hierarchy "coordinate" calculation is consistent with ordering (larger coord means strictly larger key)
 * - the key intervals covered by hierarchy "coordinates" are inclusive on the left and exclusive on the right
 * - spillover threshold is reasonably sized for very fast sort
 * - number of coordinate-indices on every depth level is small enough for fast batch-operations on bitmaps
 * - a key may correspond to multiple values, but every value has a single key
*/
class HierarchicalBitmap[K](val depth: Int, val coord: Option[Coord])(using geo: Geo[K], ord: Ordering[K]) extends Serializable:

	private var values = emptyBitmap
	private var n = 0
	private var children: Option[HashMap[Coord, HierarchicalBitmap[K]]] = None
	private var firstKey: Option[K] = None
	private var seenDifferentKeys: Boolean = false

	def all: ImmutableRoaringBitmap = values

	/**
	 * Adds the value. If the value is present, it is purged first
	 * @param key must be the same as what `geo.keyLookup(value)` would return.
	 * @throws java.lang.AssertionError if the value is already present.
	 * @return `true` if the value was new, `false` if it had to be purged first
	*/
	def add(key: K, value: Int): Boolean =

		val wasPresent = purgeValueIfPresent(value)

		values.add(value)
		n += 1
		if(children.nonEmpty) addToChild(key, value)

		if(!seenDifferentKeys) assessDiversityOfKeys(key)

		if children.isEmpty && seenDifferentKeys && (n >= geo.spilloverThreshold)
		then values.forEach{v => addToChild(geo.keyLookup(v), v)}

		!wasPresent


	/**
	 * Removes the value, returning true if value was present and false otherwise.
	 * @param key must be the same as the one supplied when the value was added.
	 */
	def remove(key: K, value: Int): Boolean =
		val wasPresentInSelf = values.contains(value)

		val childrenIsEmpty = children.fold(true)(_.isEmpty)

		val wasPresentInChildren = !childrenIsEmpty && children.fold(false): ch =>
			val coord = nextLevel(key)
			ch.get(coord).fold(false)(_.remove(key, value))

		if (wasPresentInChildren || childrenIsEmpty) && wasPresentInSelf then
			values.remove(value)
			n -= 1

		wasPresentInChildren || childrenIsEmpty && wasPresentInSelf


	private def purgeValueIfPresent(value: Int): Boolean =
		if(values.contains(value)){
			values.remove(value)
			n -= 1
			children.foreach: ch =>
				ch.valuesIterator.foreach(_.purgeValueIfPresent(value))
			true
		} else false

	private def assessDiversityOfKeys(key: K): Unit = firstKey match{
		case None =>       firstKey = Some(key)
		case Some(fKey) => seenDifferentKeys = !ord.equiv(key, fKey)
	}

	private def addToChild(key: K, value: Int): Unit =
		val coord = nextLevel(key)

		if children.isEmpty then children = Some(HashMap.empty)

		children.foreach: ch =>
			val child = ch.getOrElseUpdate(coord, new HierarchicalBitmap[K](depth + 1, Some(coord)))
			child.add(key, value)


	def iterateSorted(filter: Option[ImmutableRoaringBitmap] = None, offset: Int = 0, sortDescending: Boolean = false): Iterator[Int] = {

		val valComp: ju.Comparator[Int] = {
			val keyOrd = if(sortDescending) ord.reverse else ord
			Ordering.by(geo.keyLookup)(keyOrd)
		}

		val coordOrd: Ordering[Coord] = {
			val ascending = implicitly[Ordering[Coord]]
			if(sortDescending) ascending.reverse else ascending
		}

		given IterationInstruction = new IterationInstruction(filter, valComp, coordOrd)

		open(innerIterate(offset))
	}

	private def innerIterate(offset: Int)(using iter: IterationInstruction): OffsetOrResult =
		if(offset >= n){
			val amount = iter.filter.fold(n){filter =>
				ImmutableRoaringBitmap.andCardinality(values, filter)
			}
			Left(offset - amount)

		} else if(children.isEmpty) {

			val (filtered, amount) = iter.filter.fold(values -> n){filterBm =>
				val filtered = ImmutableRoaringBitmap.and(values, filterBm)
				filtered -> filtered.getCardinality
			}

			if(offset >= amount && offset > 0) Left(offset - amount)
			else if(amount > 0) Right{
				val res =
					if(seenDifferentKeys && amount > 1){
						val list = new ju.ArrayList[Int](amount)
						filtered.forEach((i: Int) => {list.add(i);()})
						list.sort(iter.valComp)
						list.iterator.asScala
					} else
						filtered.iterator.asScala.map(_.intValue)

				res.drop(offset)
			} else Right(Iterator.empty)

		} else {
			val childrenIter: Iterator[HierarchicalBitmap[K]] = children.iterator.flatMap(_.toSeq.sortBy(_._1)(iter.coordOrd).iterator.map(_._2))

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
		// println(s"Filtering on depth $depth at coordinate $coord with $req")
		// def logAndreject(c: Coord): Boolean = {
		// 	println(s"Rejecting child with coord $c and depth ${depth + 1}")
		// 	false
		// }
		def inner(borderFilter: Coord => Boolean, wholeChildFilter: Coord => Boolean) = MutableRoaringBitmap.or(
			children.toSeq.flatten.collect:
				//println(s"taking whole child at coord $coord and depth ${depth + 1}")
				case (coord, bm) if wholeChildFilter(coord) => bm.values
				//println(s"will filter child at coord $coord and depth ${depth + 1}")
				case (coord, bm) if borderFilter(coord) => bm.filter(req)
				//case (coord, _) if logAndreject(coord) => ???
			* //use the seq above as varargs
		)

		if(children.isEmpty){
			//println(s"No children, depth $depth")
			if(!seenDifferentKeys){
				//println("not seen different keys")
				if(values.isEmpty) values else {
					val theOnlyKey = geo.keyLookup(values.first)
					if(filterKey(theOnlyKey, req)) values
					else emptyBitmap
				}
			} else {
				val filtered = emptyBitmap
				values.forEach((v: Int) => {
					val key = geo.keyLookup(v)
					if(filterKey(key, req)) filtered.add(v)
				})
				//println(s"seen different keys, got ${filtered.getCardinality} results")
				filtered
			}
		} else req match{
			case EqualsFilter(key) =>
				val eqCoord = nextLevel(key)
				inner(eqCoord == _, _ => false)

			case MinFilter(min, _) =>
				if(coord.exists(thisLevel(min) < _)) values else {
					val minCoord = nextLevel(min)
					inner(minCoord == _, minCoord < _)
				}

			case MaxFilter(max, _) =>
				if(coord.exists(thisLevel(max) > _)) values else {
					val maxCoord = nextLevel(max)
					inner(maxCoord == _, maxCoord > _)
				}

			case IntervalFilter(from, to) =>
				val min = thisLevel(from.min)
				val max = thisLevel(to.max)
				val minIsOut: Boolean = coord.exists(min < _)
				val maxIsOut: Boolean = coord.exists(max > _)
				if(minIsOut && maxIsOut) values//println("returning all")
				else if(minIsOut) filter(to)//println("lower limit redundant, deletating to MaxFilter")
				else if(maxIsOut) filter(from)//println("upper limit redundant, deletating to MinFilter")
				else {
					assert(min == max, s"SPARQL engine algoritm error: both limits must have same bitmap-hierarchy-coordinate on depth $depth")
					//safe to go deeper with interval filter
					val minCn = nextLevel(from.min)
					val maxCn = nextLevel(to.max)
					//println(s"will analyze children with depth ${depth + 1} and $minCn <= coord <= $maxCn")
					inner(c => c == minCn || c == maxCn, c => c > minCn && c < maxCn)
				}
		}
	}

	def optimizeAndTrim(): Unit =
		values.runOptimize()
		values.trim()
		children.foreach: innerChildren =>
			if(innerChildren.nonEmpty) innerChildren.valuesIterator.foreach(_.optimizeAndTrim())

	def filterKey(key: K, filter: FilterRequest[K]): Boolean = filter match{
		case EqualsFilter(fkey) => ord.equiv(key, fkey)
		case MinFilter(min, inclusive) => if(inclusive) ord.lteq(min, key) else ord.lt(min, key)
		case MaxFilter(max, inclusive) => if(inclusive) ord.gteq(max, key) else ord.gt(max, key)
		case IntervalFilter(from, to) => filterKey(key, from) && filterKey(key, to)
	}

	private def nextLevel(key: K): Coord = geo.coordinate(key, depth + 1)
	private def thisLevel(key: K): Coord = geo.coordinate(key, depth)
	private def emptyBitmap = MutableRoaringBitmap.bitmapOf()

	def serialize(out: DataOutput, innerWriter: AnyRef => Unit): Unit =
		serializeConstructorVariables(out, innerWriter, depth, coord, geo, ord)
		serializePrivateState(out, innerWriter)

	private def serializePrivateState(out: DataOutput, innerWriter: AnyRef => Unit) =
		innerWriter(values)
		out.writeInt(n)
		innerWriter(children)
		innerWriter(firstKey)
		out.writeBoolean(seenDifferentKeys)

	private def deserializePrivateState(in: DataInput, innerReader: [T] => Class[T] => T) =
		values = innerReader(classOf[MutableRoaringBitmap])
		n = in.readInt()
		children = innerReader(classOf[Option[HashMap[Coord, HierarchicalBitmap[K]]]])
		firstKey = innerReader(classOf[Option[K]])
		seenDifferentKeys = in.readBoolean()

end HierarchicalBitmap


object HierarchicalBitmap{
	type Coord = Short
	trait Geo[K] extends Serializable{
		/** depth zero always returns zero */
		def coordinate(key: K, depth: Int): Coord
		def keyLookup(value: Int): K
		def spilloverThreshold: Int
	}

	sealed trait FilterRequest[+K]
	case class EqualsFilter[+K](key: K) extends FilterRequest[K]
	case class MinFilter[+K](min: K, inclusive: Boolean) extends FilterRequest[K]
	case class MaxFilter[+K](max: K, inclusive: Boolean) extends FilterRequest[K]
	case class IntervalFilter[+K](from: MinFilter[K], to: MaxFilter[K]) extends FilterRequest[K]

	private class IterationInstruction(
		val filter: Option[ImmutableRoaringBitmap],
		val valComp: ju.Comparator[Int],
		val coordOrd: Ordering[Coord]
	)

	private type OffsetOrResult = Either[Int, Iterator[Int]]
	private def open(res: OffsetOrResult): Iterator[Int] = res.fold(_ => Iterator.empty, identity)

	def deserialize[K](in: DataInput, innerReader: [T] => Class[T] => T): HierarchicalBitmap[K] =
		val bm = deserializeConstructorVariables[K](in, innerReader)
		bm.deserializePrivateState(in, innerReader)
		bm

	private def serializeConstructorVariables[K](
		out: DataOutput,
		innerWriter: AnyRef => Unit,
		depth: Int,
		coord: Option[Coord],
		geo: Geo[K],
		ord: Ordering[K]
	): Unit =
		out.writeInt(depth)
		innerWriter(coord)
		innerWriter(geo)
		innerWriter(ord)

	private def deserializeConstructorVariables[K](in: DataInput, innerReader: [T] => Class[T] => T) =
		val depth = in.readInt()
		val coord = innerReader(classOf[Option[Coord]])
		val geo = innerReader(classOf[Geo[K]])
		val ord = innerReader(classOf[Ordering[K]])
		new HierarchicalBitmap[K](depth, coord)(using geo, ord)

	// private def time[T](comp: => T): T = {
	// 	val start = System.nanoTime
	// 	val res = comp
	// 	val us = (System.nanoTime - start) / 1000
	// 	println(s"Elapsed $us us")
	// 	res
	// }
}
