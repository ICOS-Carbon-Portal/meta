package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.IRI
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.services.CpmetaVocab

class GeoEventProducer(cpIndex: CpIndex, metaVocab: CpmetaVocab, geoLookup: GeoLookup):
	private val geoJsonReader = GeoJsonReader()

	def getDobjEvents(dobj: IRI)(using GlobConn): Validated[Seq[GeoEvent]] =
		getHashsum(dobj, metaVocab.hasSha256sum).flatMap: objHash =>
			val idx = cpIndex.getObjEntry(objHash).idx
			val stationClusterId = getStationClusterId(dobj)

			getSingleUri(dobj, metaVocab.hasSpatialCoverage).flatMap: cov =>
				getSingleString(cov, metaVocab.asGeoJSON).map: geoJson =>
					ofOwnGeoJson(idx, geoJson, stationClusterId)
				.or:
					Validated(ofLatLonBox(idx, cov, stationClusterId))
			.or:
				getSingleUri(dobj, metaVocab.wasAcquiredBy).flatMap: acq =>
					getSingleUri(acq, metaVocab.hasSamplingPoint).flatMap: samplingPt =>
						ofSamplingPt(idx, samplingPt, stationClusterId)
					.or:
						for
							site <- getSingleUri(acq, metaVocab.wasPerformedAt)
							cov <- getSingleUri(site, metaVocab.hasSpatialCoverage)
							siteGeoJson <- getSingleString(cov, metaVocab.asGeoJSON)
						yield
							val siteClusterId = site.toString()
							ofOwnGeoJson(idx, siteGeoJson, Some(siteClusterId))
					.or:
						getSingleUri(acq, metaVocab.prov.wasAssociatedWith).map: station =>
							ofStationPt(idx, station, stationClusterId)

	private def getStationClusterId(dobj: IRI)(using GlobConn): Option[String] =
		for
			spec <- getSingleUri(dobj, metaVocab.hasObjectSpec)
				if geoLookup.datasetTypes.get(spec).contains(DatasetType.StationTimeSeries)
			acq <- getSingleUri(dobj, metaVocab.wasAcquiredBy)
			station <- getSingleUri(acq, metaVocab.prov.wasAssociatedWith)
		yield station.toString
	.result

	private def getEventsFromCollection(coll: GeometryCollection, idx: Int, clusterId: Option[String]): Seq[GeoEvent] =
		(0 until coll.getNumGeometries).map: gIdx =>
			val pt = coll.getGeometryN(gIdx)
			GeoEvent(idx, true, pt, clusterId.getOrElse(GeoLookup.getClusterId(pt)))

	private def ofOwnGeoJson(idx: Int, jsonStr: String, clusterId: Option[String]): Seq[GeoEvent] =
		geoJsonReader.read(jsonStr) match
			case coll: GeometryCollection =>
				getEventsFromCollection(coll, idx, clusterId)
			case geom =>
				Seq(GeoEvent(idx, true, geom, clusterId.getOrElse(GeoLookup.getClusterId(geom))))

	private def ofSamplingPt(idx: Int, coverage: IRI, clusterId: Option[String])(using GlobConn): Validated[Seq[GeoEvent]] =
		for
			lat <- getSingleDouble(coverage, metaVocab.hasLatitude)
			lon <- getSingleDouble(coverage, metaVocab.hasLongitude)
		yield
			val coordinate = new Coordinate(lon, lat)
			val pt = JtsGeoFactory.createPoint(coordinate)
			Seq(GeoEvent(idx, true, pt, clusterId.getOrElse(GeoLookup.getClusterId(pt))))

	private def ofLatLonBox(idx: Int, coverage: IRI, clusterId: Option[String]): Seq[GeoEvent] =
		geoLookup.latLonBoxGeometries.get(coverage).toSeq.map: jtsBbox =>
			GeoEvent(idx, true, jtsBbox, clusterId.getOrElse(GeoLookup.getClusterId(jtsBbox)))

	private def ofStationPt(idx: Int, station: IRI, clusterId: Option[String]): Seq[GeoEvent] =
		geoLookup.stationLatLons.get(station).toSeq.flatMap:
			case coll: GeometryCollection => getEventsFromCollection(coll, idx, clusterId)
			case geom => Seq(GeoEvent(idx, true, geom, clusterId.getOrElse(GeoLookup.getClusterId(geom))))

end GeoEventProducer
