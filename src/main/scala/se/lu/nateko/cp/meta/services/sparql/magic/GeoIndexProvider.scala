package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sail.Sail
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.crypto.Md5Sum
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoJson
import se.lu.nateko.cp.meta.instanceserver.Rdf4jSailConnection
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Using
import se.lu.nateko.cp.meta.core.data.LatLonBox
import scala.concurrent.Awaitable

class GeoIndexProvider(using ExecutionContext):

	def apply(sail: Sail, cpIndex: CpIndex, staticObjReader: StaticObjectReader): Future[GeoIndex] = Future(
		Future.fromTry:
			Using(sail.getConnection): conn =>
				given GlobConn = RdfLens.global(using Rdf4jSailConnection(null, Nil, conn, sail.getValueFactory))
				index(cpIndex, staticObjReader)
	).flatten

	private def index(cpIndex: CpIndex, staticObjReader: StaticObjectReader)(using conn: GlobConn): GeoIndex =
		val metaVocab = staticObjReader.metaVocab
		val geo = new GeoIndex
		val JtsGeoFactory = new GeometryFactory()
		val reader = GeoJsonReader()
		val geoLookup = GeoLookup(staticObjReader)

		def geoFeatureToJtsGeometry(gf: GeoFeature): Geometry =
			val jsonStr = GeoJson.fromFeature(gf).toString
			reader.read(jsonStr)

		def getClusterId(geom: Geometry): String =
			Md5Sum.ofStringBytes(geom.toString()).toString

		def getOwnGeoJson(idx: Int, jsonStr: String, clusterId: Option[String]): Seq[GeoEvent] =
			val geom = reader.read(jsonStr)
			geom match
				case coll: GeometryCollection =>
					(0 until coll.getNumGeometries).map: gIdx =>
						val pt = coll.getGeometryN(gIdx)
						GeoEvent(idx, true, pt, clusterId.getOrElse(getClusterId(pt)))
				case _ =>
					Seq(GeoEvent(idx, true, geom, clusterId.getOrElse(getClusterId(geom))))

		def getSamplingPt(idx: Int, coverage: IRI, clusterId: Option[String]): Validated[Seq[GeoEvent]] =
			val lat = getSingleDouble(coverage, metaVocab.hasLatitude)
			val lon = getSingleDouble(coverage, metaVocab.hasLongitude)

			val pt = lat.flatMap(a => lon.map(b => (a, b)))

			pt.map: (lat, lon) =>
				val coordinate = new Coordinate(lon, lat)
				val pt = JtsGeoFactory.createPoint(coordinate)
				Seq(GeoEvent(idx, true, pt, clusterId.getOrElse(getClusterId(pt))))

		def getLatLonBox(idx: Int, coverage: IRI, clusterId: Option[String]): Validated[Seq[GeoEvent]] = 
			staticObjReader.getLatLonBox(coverage).map(box =>
				val jtsBbox = geoFeatureToJtsGeometry(box)
				Seq(GeoEvent(idx, true, jtsBbox, clusterId.getOrElse(getClusterId(jtsBbox))))
			)

		// def getSiteLocation(idx: Int, coverage: IRI, clusterId: Option[String]) =
		// 	val location = staticObjReader.getCoverage(coverage)
		// 	location.map: gf =>
		// 		val jtsLocation = geoFeatureToJtsGeometry(gf)
		// 		Seq(GeoEvent(idx, true, jtsLocation, clusterId.getOrElse(getClusterId(jtsLocation))))

		// def getSite(idx: Int, coverage: IRI) =
		// 	staticObjReader.getSite(coverage).map(site => site.location.fold(Nil)(gf => 
		// 		val geom = geoFeatureToJtsGeometry(gf)
		// 		Seq(GeoEvent(idx, true, geom, getClusterId(geom)))))

		def getStationPt(idx: Int, coverage: IRI, clusterId: Option[String]): Validated[Seq[GeoEvent]] =
			val validatedPoint = geoLookup.stationLatLons.get(coverage).fold(Validated.error(""))(pt => Validated.ok(pt)).flatten
			validatedPoint.map(geom => Seq(GeoEvent(idx, true, geom, getClusterId(geom))))

		def getStation(item: IRI): Validated[String] =
			val acq = getSingleUri(item, metaVocab.wasAcquiredBy)

			val station = acq.flatMap(acqUri =>
				val stationUri = getSingleUri(acqUri, metaVocab.prov.wasAssociatedWith)
				stationUri
			)

			station.map(_.toString)

		def getStationClusterId(dobj: IRI): Option[String] =
			getSingleUri(dobj, metaVocab.dataObjectSpecClass).flatMap: spec =>
				val dataType = getSingleUri(spec, metaVocab.hasSpecificDatasetType)
				val datasetType = dataType.map(geoLookup.datasetTypes)
				val station = getStation(dobj)

				datasetType.map(dsType =>
					if dsType == DatasetType.StationTimeSeries then station.result else None
				)
			.result.flatten

		// ownGeoJson > samplingPt > site > latLonBox > stationPt

		// borde man spara vilket objekt eventet tillhör
		val itemsWithCoverage: Iterator[GeoEvent] = getStatements(null, RDF.TYPE, metaVocab.dataObjectClass)
			.collect:
				case Rdf4jStatement(dobj, _, _) => dobj
			.flatMap: dobj =>
				getHashsum(dobj, metaVocab.hasSha256sum).flatMap: objHash =>
					val idx = cpIndex.getObjEntry(objHash).idx
					val stationClusterId = getStationClusterId(dobj)

					if hasStatement(dobj, metaVocab.hasSpatialCoverage, null) then
						getSingleUri(dobj, metaVocab.hasSpatialCoverage).flatMap: cov =>
							// if hasStatement(cov, metaVocab.asGeoJSON, null) then
							val ownGeoJson = getSingleString(cov, metaVocab.asGeoJSON).map: geoJson =>
												getOwnGeoJson(idx, geoJson, stationClusterId)

							ownGeoJson.or:
								getLatLonBox(idx, cov, stationClusterId)
					else
						getSingleUri(dobj, metaVocab.wasAcquiredBy).flatMap: acq =>
							val stationPointEvents = 
								getSingleUri(acq, metaVocab.prov.wasAssociatedWith).flatMap: station =>
									getStationPt(idx, acq, stationClusterId)

							val samplingPointEvents = getSingleUri(acq, metaVocab.hasSamplingPoint).flatMap: samplingPt =>
								getSamplingPt(idx, acq, stationClusterId)

							val siteEvents =
								getSingleUri(acq, metaVocab.wasPerformedAt).flatMap: site =>
									getSingleUri(site, metaVocab.hasSpatialCoverage).flatMap: cov =>
										getSingleString(cov, metaVocab.asGeoJSON).map: siteGeoJson =>
											val siteClusterId = site.toString()
											getOwnGeoJson(idx, siteGeoJson, Some(siteClusterId))
										 	// site id??

							samplingPointEvents.or(siteEvents).or(stationPointEvents)
				.result.toIndexedSeq.flatten

		var objCounter = 0


		val startTime = System.currentTimeMillis()
		itemsWithCoverage.foreach(event =>
			if objCounter % 1000 == 0 then println(s"Object nbr $objCounter being processed")
			geo.putQuickly(event)
			objCounter = objCounter + 1
		)
		val endTime = System.currentTimeMillis()
		println(s"Put took ${endTime - startTime} ms")

		geo.arrangeClusters()
		val endOfAll = System.currentTimeMillis()

		println(s"Everything took ${endOfAll - startTime} ms")
		println("Total number of objects: " + objCounter)

		geo

end GeoIndexProvider

