//> using toolkit default
//> using dep "org.locationtech.jts:jts-core:1.19.0"
//> using dep "org.locationtech.jts.io:jts-io-common:1.19.0"

import org.locationtech.jts.geom.{GeometryFactory, LineString, Coordinate}
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier as DPS
import org.locationtech.jts.io.geojson.GeoJsonWriter

val factory = GeometryFactory()
val fullDataPath = os.home / "Documents" / "CP" / "nordstream_helipod_20221005.csv"
val latLonPath = fullDataPath / os.up / "nordstream_latlon.csv"

def extractLanLon(): Unit =
	val latLonLines = os.read.lines.stream(fullDataPath)
		.dropWhile(_.startsWith("#"))
		.map: line =>
			val cols = line.split(",", -1)
			s"${cols(1)},${cols(2)}\n"
	os.write.over(latLonPath, latLonLines)

//extractLanLon()

val rawLineString: LineString =
	val coords = os.read.lines.stream(latLonPath).drop(1).map: line =>
		val cols = line.split(",", -1)
		val lat = cols(0).toDouble
		val lon = cols(1).toDouble
//		println(s"$lon $lat")
		Coordinate(lon, lat)
	factory.createLineString(coords.toArray)

//rawLineString.getCoordinates.take(10).foreach(println)
println(s"Starting with ${rawLineString.getNumPoints} points")
val simple = DPS.simplify(rawLineString, 0.03)
println(s"Resulting in ${simple.getNumPoints} points")
val writer = GeoJsonWriter(3)
println(writer.write(simple))
//println(simple)
