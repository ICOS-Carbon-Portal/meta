package se.lu.nateko.cp.meta.services.sparql.magic

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.IteratorHasAsJava
import scala.util.Failure
import scala.util.Success

val JtsGeoFactory = new GeometryFactory()

trait Cluster:
	def area: Geometry
	def getFilter(bbox: Geometry, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap
	def printTree(level: Int): Unit

trait SimpleCluster extends Cluster:
	protected def objectIds = new MutableRoaringBitmap
	def filter: ImmutableRoaringBitmap = objectIds
	def addObject(dobjCov: DataObjCov): SimpleCluster
	def removeObject(dobjCov: DataObjCov): SimpleCluster
	//def addObjectId(id: Int): Unit = _objectIds.add(id)

case class GeoEvent(
	objIdx: Int,
	isAssertion: Boolean,
	geometry: Geometry,
	clusterId: String
)

class DataObjCov(val idx: Int, val geo: Geometry)

def calculateBoundingBox(shapes: Seq[Geometry]): Geometry =
	val collection = GeometryCollection(shapes.toArray, JtsGeoFactory)
	collection.getEnvelope()

class CompositeCluster(val area: Geometry, val children: IndexedSeq[Cluster]) extends Cluster:

	def printTree(level: Int): Unit =
		for (i <- 1 until level)
			print("\t")

		println(area.toString())

		for (child <- children)
			child.printTree(level + 1)

	def addCluster(c: Cluster): CompositeCluster = if !overlaps(c) then this else
		var hasAddedToChildren: Boolean = false

		val newChildren = children.map:
			case sc: SimpleCluster => sc
			case cc: CompositeCluster =>
				val newCc = cc.addCluster(c)
				if newCc.ne(cc) then hasAddedToChildren = true
				newCc

		val updatedChildren = if hasAddedToChildren then newChildren else newChildren :+ c

		CompositeCluster(area, updatedChildren)

	def removeCluster(c: SimpleCluster): CompositeCluster = if !overlaps(c) then this else
		val newChildren = children.collect:
			case sc: SimpleCluster if sc != c => sc
			case cc: CompositeCluster => cc.removeCluster(c)
		CompositeCluster(area, newChildren)

	private def overlaps(c: Cluster): Boolean =
		//TODO Investigate if there is a single test method for this (contains or overlaps)
		area.contains(c.area) || area.overlaps(c.area)

	override def getFilter(bbox: Geometry, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =

		if bbox.intersects(area) then
			ImmutableRoaringBitmap.or(children.map(_.getFilter(bbox, otherFilter)).iterator.asJava)
		else
			new MutableRoaringBitmap

end CompositeCluster


def createEmptyTopClusters: IndexedSeq[CompositeCluster] =
	val f = JtsGeoFactory
	val topLevelEnvelopes = IndexedSeq(
		new Envelope(-180, -60, 0, 90), // America
		new Envelope(-60, 60, 0, 90), // Europe
		new Envelope(60, 180, 0, 90),
		new Envelope(-180, -60, -90, 0),
		new Envelope(-60, 60, -90, 0),
		new Envelope(60, 180, -90, 0)
	) // Envelope(maxLon, minLon, maxLat, minLat)

	val europeLongitudes = IndexedSeq(-60, -30, 0, 30) 
	val europeLatitudes = IndexedSeq(90, 60, 30)

	val europeEnvelopes = ArrayBuffer[Envelope]().empty

	for (lon <- europeLongitudes)
		for (lat <- europeLatitudes)
			europeEnvelopes.append(new Envelope(lon + 30, lon, lat - 30, lat))

	val topLevelClusters = topLevelEnvelopes.map(e => CompositeCluster(f.toGeometry(e), IndexedSeq.empty)).toBuffer
	val europeClusters = europeEnvelopes.map(e => CompositeCluster(f.toGeometry(e), IndexedSeq.empty))

	for (c <- europeClusters)
		topLevelClusters(1) = topLevelClusters(1).addCluster(c)

	// topLevelClusters.foreach(_.printTree(1))

	topLevelClusters.toIndexedSeq

class DenseCluster(val area: Geometry, objectIds: MutableRoaringBitmap) extends SimpleCluster:

	override def printTree(level: Int): Unit = 
		for (i <- 1 until level)
			print("\t")

		println("dense cluster: " + area.toString())

	override def addObject(dobjCov: DataObjCov): SimpleCluster =
		// println("add object: " + dobjCov.idx)
		objectIds.add(dobjCov.idx)
		if dobjCov.geo == area then // TODO check equality
			this
		else
			val currentDataCovs = new ArrayBuffer[DataObjCov]()

			objectIds.forEach: objId =>
				currentDataCovs.addOne(DataObjCov(objId, area))
			
			currentDataCovs.addOne(dobjCov)

			SparseCluster(dobjCov.geo, currentDataCovs.toSeq, objectIds)

	override def removeObject(dobjCov: DataObjCov): SimpleCluster =
		objectIds.remove(dobjCov.idx)
		this

	override def getFilter(bbox: Geometry, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		if bbox.intersects(area) then objectIds
		else new MutableRoaringBitmap


class SparseCluster(val area: Geometry, children: Seq[DataObjCov], objectIds: MutableRoaringBitmap) extends SimpleCluster:

	override def printTree(level: Int): Unit = 
		for (i <- 1 until level)
			print("\t")

		println("sparse cluster: " + area.toString())

	//TODO: Fix bug related to sparse cluster creation (sparse cluster should not be created with one point as area)
	override def addObject(dobjCov: DataObjCov): SimpleCluster =
		//TODO Consider which data structure to use for fast append/prepend
		val newChildren = children :+ dobjCov
		objectIds.add(dobjCov.idx)

		if (area.contains(dobjCov.geo)) then
			SparseCluster(area, newChildren, objectIds)
		else
			SparseCluster(calculateBoundingBox(newChildren.map(_.geo)), newChildren, objectIds)

	override def removeObject(dobjCov: DataObjCov): SimpleCluster =
		val newChildren = children.filter(_.idx != dobjCov.idx)
		val newGeometries = newChildren.map(_.geo).toSet
		objectIds.remove(dobjCov.idx)

		if newGeometries.size == 1 then
			DenseCluster(newGeometries.head, objectIds)
		else
			SparseCluster(calculateBoundingBox(newChildren.map(_.geo)), newChildren, objectIds)

	override def getFilter(bbox: Geometry, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =

		if bbox.contains(area) then
			objectIds
		else if bbox.intersects(area) then

			val otherTest: Int => Boolean = otherFilter.fold[Int => Boolean](_ => true)(_.contains)
			val matchingObjects = new MutableRoaringBitmap

			children.foreach: dobjCov =>
				if otherTest(dobjCov.idx) && bbox.intersects(dobjCov.geo)
				then matchingObjects.add(dobjCov.idx)

			matchingObjects
		else
			new MutableRoaringBitmap

class GeoIndex:
	val allClusters = mutable.Map.empty[String, SimpleCluster]
	private var topClusters: IndexedSeq[CompositeCluster] = createEmptyTopClusters
	def compositeClusters = topClusters

	def put(event: GeoEvent): Unit = innerPut(event, false)
	def putQuickly(event: GeoEvent): Unit = innerPut(event, true)

	private def innerPut(event: GeoEvent, quick: Boolean): Unit =
		//TODO Support the scenario when event.isAssertion == false
		val clusterExists = allClusters.contains(event.clusterId)
		val currentCluster = allClusters.getOrElseUpdate(event.clusterId, DenseCluster(event.geometry, new MutableRoaringBitmap))
		val updatedCluster = currentCluster.addObject(DataObjCov(event.objIdx, event.geometry))
		val clusterChanged = clusterExists && (updatedCluster.ne(currentCluster))
		if !clusterExists || clusterChanged then
			allClusters.update(event.clusterId, updatedCluster)
		if !quick then
			if clusterChanged then
				topClusters = topClusters.map(_.removeCluster(currentCluster))
			if !clusterExists || clusterChanged then placeCluster(updatedCluster)

	private def placeCluster(cluster: SimpleCluster): Unit =
		topClusters = topClusters.map(_.addCluster(cluster))

	def arrangeClusters(): Unit =
		allClusters.values.foreach(placeCluster)

	def getFilter(bbox: Geometry, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		ImmutableRoaringBitmap.or(topClusters.map(_.getFilter(bbox, otherFilter)).iterator.asJava)
