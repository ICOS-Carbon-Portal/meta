package se.lu.nateko.cp.meta.services.sparql.magic

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoJson
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.Position

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.IteratorHasAsJava
import scala.util.Failure
import scala.util.Success
import javax.xml.crypto.Data
import akka.http.scaladsl.settings.ParserSettings.ErrorLoggingVerbosity.Simple

case class GeoEvent(
	objIdx: Int,
	isAssertion: Boolean,
	geometry: Geometry,
	clusterId: String
)

class DataObjCov(val idx: Int, val geo: Geometry)

def calculateBoundingBox(shapes: Seq[Geometry]): Geometry =
	val f = GeometryFactory()
	val collection = GeometryCollection(shapes.toArray, f)
	collection.getEnvelope()


case class CompositeCluster(area: Geometry, var children: IndexedSeq[Cluster]) extends Cluster:

	def printTree(level: Int): Unit =
		for (i <- 1 until level) {
			print("\t")
		}
		println(area.toString())
		
		for (child <- children) {
			child.printTree(level + 1)
		}

	def allSimpleClusters(c: IndexedSeq[Cluster]): Boolean =
		c.forall:
			case _: SimpleCluster => true
			case _ => false
			
	def addCluster(c: Cluster): CompositeCluster =
		c match
			case sc: SimpleCluster => addSimpleCluster(sc)
			case cc: CompositeCluster => addCompositeCluster(cc)

	def addCompositeCluster(c: CompositeCluster): CompositeCluster =	
		if area.contains(c.area) then
			CompositeCluster(area, c +: children)
		else this	


	//TODO: Test this function
	def addSimpleCluster(c: SimpleCluster): CompositeCluster =
		if area.intersects(c.area) then
			if (allSimpleClusters(c +: children) || children.isEmpty) then
				CompositeCluster(area, c +: children)
			else
				var addedToSubCluster = false
				children = children.map {
					case sc: SimpleCluster => throw Error("A composite cluster can not contain both simple and composite clusters")
					case cc: CompositeCluster =>
						val newCluster = cc.addCluster(c)
						if newCluster.children != children then
							addedToSubCluster = true
							newCluster
						else cc
				}

				if !addedToSubCluster then
					c match
						case cc: CompositeCluster => CompositeCluster(area, c +: children)
						case sc: SimpleCluster => throw Error("Simple cluster didn't fit in any of top clusters")
				else
					this
		else this


	override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =

		val jtsBbox = getBoundingBoxAsJtsGeometry(bbox)

		if jtsBbox.intersects(area) then
			ImmutableRoaringBitmap.or(children.map(_.getFilter(bbox, otherFilter)).iterator.asJava)
		else
			new MutableRoaringBitmap

end CompositeCluster

def getBoundingBoxAsJtsGeometry(bbox: LatLonBox): Geometry =
	val point1 = new Coordinate(bbox.max.lon, bbox.max.lat)
	val point2 = new Coordinate(bbox.min.lon, bbox.min.lat)

	val boundingBox = new Envelope(point1)
	boundingBox.expandToInclude(point2)

	val f = GeometryFactory()

	f.toGeometry(boundingBox)


def createEmptyTopClusters(f: GeometryFactory): IndexedSeq[CompositeCluster] =
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

trait Cluster:
	def area: Geometry
	def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap
	def printTree(level: Int): Unit

trait SimpleCluster extends Cluster:
	protected def objectIds = new MutableRoaringBitmap
	def filter: ImmutableRoaringBitmap = objectIds
	def addObject(dobjCov: DataObjCov): SimpleCluster
	def removeObject(dobjCov: DataObjCov): SimpleCluster
	//def addObjectId(id: Int): Unit = _objectIds.add(id)

class GeoIndex:
	val f = GeometryFactory()
	val allClusters = mutable.Map.empty[String, SimpleCluster]
	private var topClusters: IndexedSeq[CompositeCluster] = createEmptyTopClusters(f)

	class DenseCluster(val area: Geometry, objectIds: MutableRoaringBitmap) extends SimpleCluster:

		override def printTree(level: Int): Unit = 
			for (i <- 1 until level) {
				print("\t")
			}
			println("dense cluster: " + area.toString())

		override def addObject(dobjCov: DataObjCov): SimpleCluster =
			// println("add object: " + dobjCov.idx)
			objectIds.add(dobjCov.idx)
			if dobjCov.geo == area then
				this
			else
				val currentDataCovs = new ArrayBuffer[DataObjCov]()

				objectIds.forEach: objId =>
					currentDataCovs.addOne(DataObjCov(objId, area))

				SparseCluster(dobjCov.geo, currentDataCovs.toSeq ++ Seq(dobjCov), objectIds)

		override def removeObject(dobjCov: DataObjCov): SimpleCluster =
			objectIds.remove(dobjCov.idx)
			this

		override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
			val bboxGeometry = getBoundingBoxAsJtsGeometry(bbox)

			if bboxGeometry.intersects(area) then objectIds
			else new MutableRoaringBitmap


	class SparseCluster(val area: Geometry, children: Seq[DataObjCov], objectIds: MutableRoaringBitmap) extends SimpleCluster:


		override def printTree(level: Int): Unit = 
			for (i <- 1 until level) {
				print("\t")
			}
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
			val newGeometries = newChildren.map(_.geo)

			//TODO Will crash for empty newGeometries
			// newGeometries should never be empty => return optional from remove instead?
			if newGeometries.forall(_ == newGeometries.head) then
				DenseCluster(newGeometries.head, objectIds)
			else
				objectIds.remove(dobjCov.idx)
				SparseCluster(calculateBoundingBox(newChildren.map(_.geo)), newChildren, objectIds)

		override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
			val jtsBbox = getBoundingBoxAsJtsGeometry(bbox)

			if jtsBbox.contains(area) then
				objectIds
			else if jtsBbox.intersects(area) then

				val otherTest: Int => Boolean = otherFilter.fold[Int => Boolean](_ => true)(_.contains)
				val matchingObjects = new MutableRoaringBitmap

				children.foreach: dobjCov =>
					if otherTest(dobjCov.idx) && jtsBbox.intersects(dobjCov.geo)
					then matchingObjects.add(dobjCov.idx)

				matchingObjects
			else
				new MutableRoaringBitmap

	def put(event: GeoEvent): Unit =
		val updatedCluster = allClusters
			.getOrElseUpdate(event.clusterId, DenseCluster(event.geometry, new MutableRoaringBitmap))
			.addObject(DataObjCov(event.objIdx, event.geometry))
		allClusters.update(event.clusterId, updatedCluster)

	def placeCluster(cluster: SimpleCluster): Unit =
		topClusters = topClusters.map(_.addCluster(cluster))
		// topClusters.foreach(_.printTree(1))


	def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		ImmutableRoaringBitmap.or(topClusters.map(_.getFilter(bbox, otherFilter)).iterator.asJava)
