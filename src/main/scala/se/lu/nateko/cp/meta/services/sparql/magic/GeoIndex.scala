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


case class GeoEvent(
	objIdx: Int,
	isAssertion: Boolean,
	geometry: Geometry,
	clusterId: String
)

def calculateBoundingBox(shapes: Seq[Geometry]): Geometry =
	val f = GeometryFactory()
	val collection = GeometryCollection(shapes.toArray, f)
	collection.getEnvelope()

class DataObjCov(val idx: Int, val geo: Geometry)

trait Cluster:
	def area: Geometry
	def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap

trait SimpleCluster extends Cluster:
	protected def objectIds = new MutableRoaringBitmap
	def filter: ImmutableRoaringBitmap = objectIds
	def addObject(objIdx: Int, geometry: Geometry): SimpleCluster
	def removeObject(objIdx: Int, geometry: Geometry): SimpleCluster
	//def addObjectId(id: Int): Unit = _objectIds.add(id)


case class CompositeCluster(area: Geometry, children: IndexedSeq[Cluster]) extends Cluster:

	//TODO Consider that the cluster may need to be added to one of the children, not on the top level as a direct child
	def addChild(c: Cluster): CompositeCluster = CompositeCluster(area, children :+ c)

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


class DenseCluster(val area: Geometry, objectIds: MutableRoaringBitmap) extends SimpleCluster:

	//TODO Use DobjCov class instead of the pair of arguments
	override def addObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		if geometry == area then
			objectIds.add(objIdx)
			this
		else
			val incomingDataCov = DataObjCov(objIdx, geometry)
			val currentDataCovs = new ArrayBuffer[DataObjCov]()

			objectIds.forEach: objId =>
				currentDataCovs.addOne(DataObjCov(objId, area))

			SparseCluster(geometry, currentDataCovs.toSeq ++ Seq(incomingDataCov), objectIds)

	override def removeObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		objectIds.remove(objIdx)
		this

	override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		val bboxGeometry = getBoundingBoxAsJtsGeometry(bbox)

		if bboxGeometry.intersects(area) then filter
		else new MutableRoaringBitmap

class SparseCluster(val area: Geometry, children: Seq[DataObjCov], objectIds: MutableRoaringBitmap) extends SimpleCluster:

	override def addObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		//TODO Consider which data structure to use for fast append/prepend
		val newChildren = children :+ DataObjCov(objIdx, geometry)
		objectIds.add(objIdx)

		if (area.contains(geometry)) then
			SparseCluster(area, newChildren, objectIds)
		else
			SparseCluster(calculateBoundingBox(newChildren.map(_.geo)), newChildren, objectIds)

	override def removeObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		// TODO Review this method
		val newChildren = children.filter(_.idx != objIdx)
		val newGeometries = newChildren.map(_.geo)

		//TODO Will crash for empty newGeometries
		if newGeometries.forall(_ == newGeometries.head) then
			DenseCluster(newGeometries.head, objectIds)
		else
			objectIds.remove(objIdx)
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


def createEmptyTopClusters(f: GeometryFactory): IndexedSeq[CompositeCluster] =
	//TODO Create child subclusters for the European cluster
	val envelopes = IndexedSeq(
		new Envelope(-180, -60, 0, 90),
		new Envelope(-60, 60, 0, 90), // Europe
		new Envelope(60, 180, 0, 90),
		new Envelope(-180, -60, -90, 0),
		new Envelope(-60, 60, -90, 0),
		new Envelope(60, 180, -90, 0) 
	)

	envelopes.map(e => CompositeCluster(f.toGeometry(e), IndexedSeq.empty))

//TODO Make this class to a module with internal classes, to share the GeometryFactory
class GeoIndex:
	val f = GeometryFactory()
	private val allClusters = mutable.Map.empty[String, SimpleCluster]
	private var topClusters: IndexedSeq[CompositeCluster] = createEmptyTopClusters(f)

	def put(event: GeoEvent): Unit =
		val updatedCluster = allClusters
			.getOrElseUpdate(event.clusterId, DenseCluster(event.geometry, new MutableRoaringBitmap))
			.addObject(event.objIdx, event.geometry)
		allClusters.update(event.clusterId, updatedCluster)

	def placeCluster(cluster: SimpleCluster): Unit =
		topClusters = topClusters.map(_.addChild(cluster))

	def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		ImmutableRoaringBitmap.or(topClusters.map(_.getFilter(bbox, otherFilter)).iterator.asJava)
