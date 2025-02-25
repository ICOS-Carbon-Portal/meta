package se.lu.nateko.cp.meta.test.services.upload.geocov

import org.locationtech.jts.geom.{Coordinate, Point}
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.geocov.LabeledJtsGeo


object TestGeometries:

	val jtsPoint: Point = JtsGeoFactory.createPoint(Coordinate(5.449391234467299, 48.61221138915232))

	val labeledPoint: LabeledJtsGeo = LabeledJtsGeo(jtsPoint, Seq("point"))

	val labeledPolygon1: LabeledJtsGeo = mkPolygon("polygon1",
		(5.4382483197915406, 48.617131177792345),
		(5.44637989429566, 48.60593059652089),
		(5.459932518468463, 48.61007510117048),
		(5.466200607148494, 48.61343526064317),
		(5.4382483197915406, 48.617131177792345)
	)

	val labeledPolygon2: LabeledJtsGeo = mkPolygon("polygon2",
		(5.44083865788113, 48.607808086259695),
		(5.443512411248605, 48.603907305494886),
		(5.441645027944418, 48.60096047278162),
		(5.450387777052811, 48.60135339374344),
		(5.450260455463962, 48.60876218821079),
		(5.44083865788113, 48.607808086259695)
	)

	val labeledPolygon3: LabeledJtsGeo = mkPolygon("polygon3",
		(5.463743736552686, 48.60778949699633),
		(5.461633648385572, 48.603437579496955),
		(5.470475922608301, 48.605729857594014),
		(5.470978324553101, 48.610247666322266),
		(5.465100221802601, 48.61021444861535),
		(5.463743736552686, 48.60778949699633)
	)

	def mkPolygon(label: String, longLats: (Double, Double)*): LabeledJtsGeo =
		val coords = longLats.map{case (lon, lat) => Coordinate(lon, lat)}.toArray
		val poly = JtsGeoFactory.createPolygon(JtsGeoFactory.createLinearRing(coords))
		LabeledJtsGeo(poly, Seq(label))

end TestGeometries
