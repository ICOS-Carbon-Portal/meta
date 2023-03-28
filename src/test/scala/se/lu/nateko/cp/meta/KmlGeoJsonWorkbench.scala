package se.lu.nateko.cp.meta

import se.lu.nateko.cp.meta.core.data.{Polygon => GeoPolygon, Position, Circle}
import com.scalakml.io.{KmzFileReader, KmlPrintWriter}
import com.scalakml.kml.*
import xml.PrettyPrinter
import se.lu.nateko.cp.meta.core.data.GeoFeature
import spray.json.JsObject
import java.io.File
import spray.json.{JsNull, JsValue}
import se.lu.nateko.cp.meta.core.data.GeoJson
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.etcupload.StationId
import java.net.URL
import scala.io.Source

object KmlGeoJsonWorkbench {

	val workDir = "/home/oleg/Downloads/ETC_kmz/"

	def saveKmzs = {
		import sys.process.*
		for((id, url) <- getKmzUrls){
			(url #> new File(workDir + id.id + ".kmz")).!!
		}
	}

	def parseKmzs: Iterable[(StationId, JsValue)] = {
		new File(workDir).listFiles().map{file =>
			val id = StationId.unapply(file.getName.stripSuffix(".kmz")).get
			id -> getGeoJson(file)
		}.sortBy(_._1.id)
	}

	def getKmzUrls: Iterable[(StationId, URL)] = {
		val lines = Source.fromURL("http://gaia.agraria.unitus.it:89/cpmeta?type=station").getLines()
		val header = lines.next().split("\t", -1)
		val idIdx = header.indexOf("SITE_ID")
		val kmzIdx = header.indexOf("URL_KMZ")
		lines.map{line =>
			val cells = line.split("\t", -1)
			val urlBase = cells(kmzIdx).trim
			StationId.unapply(cells(idIdx).trim).filterNot(_ => urlBase.isEmpty).map{
				_ -> new URL(urlBase + "/download")
			}
		}.flatten.toIndexedSeq
	}

	def getGeoJson(kmz: File): JsValue = {
		val areas: Seq[GeoFeature] = new KmzFileReader()
			.getKmlFromKmzFile(kmz).flatten.flatMap(_.feature)
			.collect{
				case f: Folder => f.features
			}.flatten.collect{
				case d: Document if isRelevantDoc(d) => d.features
			}.flatten.collect{
				case f: Folder => f.features
				case pm: Placemark => Seq(pm)
			}.flatten.collect{
				case pm: Placemark =>
					val lbl = pm.featurePart.name.map(_.trim)
					pm.geometry.toSeq.collect{
						case poly: Polygon => processPolygon(poly)
						case p: Point => p.coordinates.collect{
							case Coordinate(Some(lon), Some(lat), _) => Position.ofLatLon(lat, lon)
						}
					}.flatten.map(_.withOptLabel(lbl))
			}.flatten.toList

		areas match{
			case Nil => JsNull
			case feat :: Nil => GeoJson.fromFeature(feat)
			case multi => GeoJson.fromFeature(FeatureCollection(multi, None, None))
		}

	}

	private def processPolygon(poly: Polygon): Option[GeoFeature] = {
		poly.outerBoundaryIs.flatMap(_.linearRing).flatMap(_.coordinates).map{coords =>

			val posOriginal = coords.collect{
				case Coordinate(Some(lon), Some(lat), altOpt) => Position(lat, lon, altOpt.filterNot(_ == 0).map(_.toFloat), None, None)
			}

			val positions = (if(areClockwise(posOriginal)) posOriginal.reverse else posOriginal).dropRight(1)

			getCircle(positions).getOrElse(GeoPolygon(positions, None, None))
		}
	}

	def isRelevantDoc(d: Document): Boolean = {
		d.featurePart.name.fold(false){n =>
			n.trim == "CP areas" ||
			n.trim == "Target area" ||
			//n.contains("_LCT") ||
			//n.contains("_CP") ||
			//n.contains("(CPs)") ||
			n.contains("_TA")
		}
	}

	def areClockwise(pos: Seq[Position]): Boolean = {
		pos.sliding(2, 1).map{ps => (ps(1).lon - ps(0).lon) * (ps(1).lat + ps(0).lat)}.sum >= 0
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
			Some(Circle(Position.ofLatLon(centerLat, centerLon), (averDist * 6371000).toFloat, None, None))
		else
			None
	}


}
