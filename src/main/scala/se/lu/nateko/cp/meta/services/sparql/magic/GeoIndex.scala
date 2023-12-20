package se.lu.nateko.cp.meta.services.sparql.magic

import org.roaringbitmap.RoaringBitmap
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.algorithm.ConvexHull
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoJson
import scala.collection.mutable.ArrayBuffer
import se.lu.nateko.cp.meta.core.data.Position
import scala.util.Success
import scala.util.Failure
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Polygon
import org.roaringbitmap.buffer.ImmutableRoaringBitmap
import org.roaringbitmap.buffer.MutableRoaringBitmap


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

case class CompositeCluster(box: Geometry, children: IndexedSeq[Cluster]) extends Cluster: // this should be cluster, using SimpleCluster for now to debug
	override val area: Geometry = calculateBoundingBox(children.map(_.area))

	def addChild(c: Cluster): CompositeCluster = CompositeCluster(box, children :+ c)

	override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		val jtsBbox = getBoundingBoxAsJtsGeometry(bbox)
		if jtsBbox.contains(box) then
			val childrenFilter = children.map(_.getFilter(bbox, otherFilter)).fold(new MutableRoaringBitmap)((b1, b2)=>
				ImmutableRoaringBitmap.or(b1, b2)
			) // return all filters
			otherFilter.fold(childrenFilter)(other => ImmutableRoaringBitmap.and(other, childrenFilter))
		else if jtsBbox.intersects(area) then
			var matchingObjects = new MutableRoaringBitmap
			val matchingIds = children.filter(_.area.intersects(jtsBbox)).map(_.getFilter(bbox, otherFilter))

			matchingIds.foreach(ids =>
				matchingObjects = ImmutableRoaringBitmap.or(matchingObjects, ids)
			)

			otherFilter.fold(matchingObjects)(other => ImmutableRoaringBitmap.and(other, matchingObjects))
		else
			new MutableRoaringBitmap

def getBoundingBoxAsJtsGeometry(bbox: LatLonBox): Geometry =
	val point1 = new Coordinate(bbox.max.lon, bbox.max.lat)
	val point2 = new Coordinate(bbox.min.lon, bbox.min.lat)

	val boundingBox = new Envelope(point1)
	boundingBox.expandToInclude(point2)

	val f = GeometryFactory()

	f.toGeometry(boundingBox)

trait SimpleCluster(area: Geometry) extends Cluster:
	protected val _objectIds = new MutableRoaringBitmap
	def objectIds: ImmutableRoaringBitmap = _objectIds
	def addObject(objIdx: Int, geometry: Geometry): SimpleCluster
	def removeObject(objIdx: Int, geometry: Geometry): SimpleCluster
	//def addObjectId(id: Int): Unit = _objectIds.add(id)

case class DenseCluster(area: Geometry) extends SimpleCluster(area):

	override def addObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		if geometry == area then
			_objectIds.add(objIdx)
			this
		else
			val incomingDataCov = DataObjCov(objIdx, geometry)
			val currentDataCovs = new ArrayBuffer[DataObjCov]()

			// would it be better to keep a list?
			val it = _objectIds.iterator()
			while it.hasNext() do
				val objId = it.next()
				currentDataCovs.addOne(DataObjCov(objId, area))

			SparseCluster(geometry, currentDataCovs.toSeq ++ Seq(incomingDataCov))

	override def removeObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		_objectIds.remove(objIdx)
		this

	override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		val bboxGeometry = getBoundingBoxAsJtsGeometry(bbox)

		if bboxGeometry.intersects(area) then
			otherFilter.fold(_objectIds)(other => ImmutableRoaringBitmap.and(other, _objectIds))
		else new MutableRoaringBitmap

case class SparseCluster(area: Geometry, children: Seq[DataObjCov]) extends SimpleCluster(area):

	children.foreach(child => _objectIds.add(child.idx))

	override def addObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		val newChildren = children :+ DataObjCov(objIdx, geometry)
		_objectIds.add(objIdx)

		if (area.contains(geometry)) then
			SparseCluster(area, newChildren)
		else
			SparseCluster(calculateBoundingBox(newChildren.map(_.geo)), newChildren)

	override def removeObject(objIdx: Int, geometry: Geometry): SimpleCluster =
		val newChildren = children.filter(_.idx != objIdx)
		val newGeometries = newChildren.map(_.geo)

		if (newGeometries.forall(_ == newGeometries.head)) then
			DenseCluster(newGeometries.head)
		else
			_objectIds.remove(objIdx)
			SparseCluster(calculateBoundingBox(newChildren.map(_.geo)), newChildren)

	override def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		val jtsBbox = getBoundingBoxAsJtsGeometry(bbox)

		if jtsBbox.contains(area) then
			otherFilter.fold(_objectIds)(other => ImmutableRoaringBitmap.and(other, _objectIds))
		else if jtsBbox.intersects(area) then
			val matchingObjects = new MutableRoaringBitmap
			val matchingIds = children.filter(_.geo.intersects(jtsBbox)).map(_.idx)

			matchingIds.foreach(matchingObjects.add)
			otherFilter.fold(matchingObjects)(other => ImmutableRoaringBitmap.and(other, matchingObjects))
		else
			new MutableRoaringBitmap


def createEmptyTopClusters(f: GeometryFactory): IndexedSeq[CompositeCluster] =
	val envelopes = IndexedSeq(
		new Envelope(-180, -60, 0, 90),
		new Envelope(-60, 60, 0, 90), // Europe
		new Envelope(60, 180, 0, 90),
		new Envelope(-180, -60, -90, 0),
		new Envelope(-60, 60, -90, 0),
		new Envelope(60, 180, -90, 0) 
	)

	envelopes.map(e => CompositeCluster(f.toGeometry(e), IndexedSeq.empty))

class GeoIndex:
	val f = GeometryFactory()
	var allClusters: Map[String, SimpleCluster] = Map.empty
	var topClusters: IndexedSeq[CompositeCluster] = createEmptyTopClusters(f)

	// start with splitting the world in boxes
	// fill boxes with clusters
	def createNewCluster(event: GeoEvent): SimpleCluster =
		DenseCluster(event.geometry).addObject(event.objIdx, event.geometry)

	def put(event: GeoEvent): Unit =
		val existingCluster = allClusters.get(event.clusterId)
		val updatedCluster = existingCluster.map(_.addObject(event.objIdx, event.geometry)).getOrElse(createNewCluster(event))
		allClusters = allClusters.updated(event.clusterId, updatedCluster)

		val found = topClusters.zipWithIndex.collect{
			case tc if tc._1.box.intersects(updatedCluster.area) => tc
		}

		found.foreach((cluster, index) => 
			topClusters = topClusters.updated(index, topClusters(index).addChild(updatedCluster))
		)

	def getFilter(bbox: LatLonBox, otherFilter: Option[ImmutableRoaringBitmap]): ImmutableRoaringBitmap =
		val jtsBbox = getBoundingBoxAsJtsGeometry(bbox)
		val intersectingClusters = topClusters.filter(_.box.intersects(jtsBbox))

		intersectingClusters.map(c => c.getFilter(bbox, otherFilter)).fold(new MutableRoaringBitmap)((b1, b2) =>
			ImmutableRoaringBitmap.or(b1, b2))
