package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.IRI
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated

class GeoEventProducer(staticObjReader: StaticObjectReader)(using conn: GlobConn):
	val metaVocab = staticObjReader.metaVocab
	val geoJsonReader = GeoJsonReader()
	val geoLookup = GeoLookup(staticObjReader)

	def reader = geoJsonReader

	def getEventsFromCollection(coll: GeometryCollection, idx: Int, clusterId: Option[String]): Seq[GeoEvent] =
		(0 until coll.getNumGeometries).map: gIdx =>
			val pt = coll.getGeometryN(gIdx)
			GeoEvent(idx, true, pt, clusterId.getOrElse(GeoLookup.getClusterId(pt)))

	def ofOwnGeoJson(idx: Int, jsonStr: String, clusterId: Option[String]): Seq[GeoEvent] =
		val geom = reader.read(jsonStr)
		geom match
			case coll: GeometryCollection => getEventsFromCollection(coll, idx, clusterId)
			case _ => Seq(GeoEvent(idx, true, geom, clusterId.getOrElse(GeoLookup.getClusterId(geom))))

	def ofSamplingPt(idx: Int, coverage: IRI, clusterId: Option[String]): Validated[Seq[GeoEvent]] =
		val lat = getSingleDouble(coverage, metaVocab.hasLatitude)
		val lon = getSingleDouble(coverage, metaVocab.hasLongitude)

		val pt = lat.flatMap(a => lon.map(b => (a, b)))

		pt.map: (lat, lon) =>
			val coordinate = new Coordinate(lon, lat)
			val pt = JtsGeoFactory.createPoint(coordinate)
			Seq(GeoEvent(idx, true, pt, clusterId.getOrElse(GeoLookup.getClusterId(pt))))

	def ofLatLonBox(idx: Int, coverage: IRI, clusterId: Option[String]): Seq[GeoEvent] =
		val latLonBoxClusterID = geoLookup.latLonBoxIds.get(coverage)
		val jtsBbox = latLonBoxClusterID.flatMap(id =>
			geoLookup.latLonBoxGeometries.get(id))

		jtsBbox.toSeq.map: bbox =>
			GeoEvent(idx, true, bbox, clusterId.getOrElse(latLonBoxClusterID.toString()))

	def ofStationPt(idx: Int, station: IRI, clusterId: Option[String]): Seq[GeoEvent] =
		geoLookup.stationLatLons.get(station).toSeq.flatMap: geom =>
			geom match
				case coll: GeometryCollection => getEventsFromCollection(coll, idx, clusterId)
				case geom => Seq(GeoEvent(idx, true, geom, clusterId.getOrElse(GeoLookup.getClusterId(geom))))
