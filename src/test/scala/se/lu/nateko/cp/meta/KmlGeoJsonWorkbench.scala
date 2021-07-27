package se.lu.nateko.cp.meta

import se.lu.nateko.cp.meta.core.data.{Polygon => GeoPolygon, Position, Circle}
import com.scalakml.io.{KmzFileReader, KmlPrintWriter}
import com.scalakml.kml._
import xml.PrettyPrinter

object KmlGeoJsonWorkbench {

	def getKml = new KmzFileReader().getKmlFromKmzFile("/home/oleg/Downloads/BE-Bra_spatial_data.kmz").flatten

	def getAreas: Seq[Either[Circle, GeoPolygon]] = getKml.flatMap(_.feature)
		.collect{
			case f: Folder => f.features
		}.flatten.collect{
			case d: Document if isRelevantDoc(d) => d.features
		}.flatten.collect{
			case f: Folder => f.features
		}.flatten.collect{
			case pm: Placemark =>
				val coordSets = pm.geometry.toSeq.collect{
					case poly: Polygon => poly.outerBoundaryIs
				}.flatten.flatMap(_.linearRing).flatMap(_.coordinates)

				assert(coordSets.size == 1, "Expected exactly one LinearRing inside Placemark\n" + pm.toString)

				val coords = coordSets.head
				val lbl = pm.featurePart.name.map(_.trim)
				val positions = coords.collect{
					case Coordinate(Some(lon), Some(lat), altOpt) => Position(lat, lon, altOpt.filterNot(_ == 0).map(_.toFloat), None)
				}.dropRight(1)

				getCircle(positions).toLeft(GeoPolygon(positions, lbl))
		}

	def isRelevantDoc(d: Document): Boolean = {
		d.featurePart.name.fold(false)(n => n.trim == "CP areas" || n.contains("_LCT"))
	}

	def readWrite: Unit = {
		val pretty = new PrettyPrinter(80, 3)
		val writer = new KmlPrintWriter("/home/oleg/Downloads/BE-Maa_out.kml")
		getKml.foreach(kml => writer.write(kml, pretty))
		println("KML written")
	}

	def getCircle(pos: Seq[Position]): Option[Circle] = if(pos.size < 8) None else{
		val n = pos.size

		val centerLat = pos.map(_.lat).sum / n
		val centerLon = pos.map(_.lon).sum / n
		val rLon = Math.cos(Math.toRadians(centerLat))

		val centerDists = pos.map{ p => //good approximation only for small distances
			val dlat = Math.toRadians(p.lat - centerLat)
			val dlon = Math.toRadians(p.lon - centerLon) * rLon
			Math.sqrt(dlat * dlat + dlon * dlon)
		}

		val averDist = centerDists.sum / n
		val deviations = centerDists.map(dist => Math.abs(averDist - dist))
		val maxDeviation = deviations.max / averDist

		if(maxDeviation < 0.03)
			Some(Circle(Position(centerLat, centerLon, None, None), (averDist * 6371000).toFloat, None))
		else
			None
	}
}
