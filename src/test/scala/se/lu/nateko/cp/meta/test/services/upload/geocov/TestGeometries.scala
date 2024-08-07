package se.lu.nateko.cp.meta.test.services.upload.geocov

import org.locationtech.jts.geom.Coordinate
import se.lu.nateko.cp.meta.services.sparql.magic.JtsGeoFactory
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovClustering.getMinGeometryDistance
import se.lu.nateko.cp.meta.services.upload.geocov.GeoCovMerger.LabeledJtsGeo

object TestGeometries:
	val jtsPoint = JtsGeoFactory.createPoint(new Coordinate(5.449391234467299, 48.61221138915232))
	val labeledPoint = LabeledJtsGeo(jtsPoint, Seq("point"))

	val polygonCoordinates1: Array[Coordinate] = Array(
		new Coordinate(5.4382483197915406, 48.617131177792345),
		new Coordinate(5.44637989429566, 48.60593059652089),
		new Coordinate(5.459932518468463, 48.61007510117048),
		new Coordinate(5.466200607148494, 48.61343526064317),
		new Coordinate(5.4382483197915406, 48.617131177792345)
	)
	
	val polygonCoordinates2: Array[Coordinate] = Array(
		new Coordinate(5.44083865788113, 48.607808086259695),
		new Coordinate(5.443512411248605, 48.603907305494886),
		new Coordinate(5.441645027944418, 48.60096047278162),
		new Coordinate(5.450387777052811, 48.60135339374344),
		new Coordinate(5.450260455463962, 48.60876218821079),
		new Coordinate(5.44083865788113, 48.607808086259695)
	)

	val polygonCoordinates3: Array[Coordinate] = Array(
		new Coordinate(5.463743736552686, 48.60778949699633),
		new Coordinate(5.461633648385572, 48.603437579496955),
		new Coordinate(5.470475922608301, 48.605729857594014),
		new Coordinate(5.470978324553101, 48.610247666322266),
		new Coordinate(5.465100221802601, 48.61021444861535),
		new Coordinate(5.463743736552686, 48.60778949699633)
	)

	val jtsPolygon1 = JtsGeoFactory.createPolygon(JtsGeoFactory.createLinearRing(polygonCoordinates1))
	val jtsPolygon2 = JtsGeoFactory.createPolygon(JtsGeoFactory.createLinearRing(polygonCoordinates2))
	val jtsPolygon3 = JtsGeoFactory.createPolygon(JtsGeoFactory.createLinearRing(polygonCoordinates3))

	// val epsDist = getMinGeometryDistance(jtsPolygon1, jtsPolygon3)

	// println("eps dist: " + epsDist)
	val labeledPolygon1 = LabeledJtsGeo(jtsPolygon1, Seq("polygon1"))
	val labeledPolygon2 = LabeledJtsGeo(jtsPolygon2, Seq("polygon2"))
	val labeledPolygon3 = LabeledJtsGeo(jtsPolygon3, Seq("polygon3"))

	val epsPoint1 = JtsGeoFactory.createPoint(new Coordinate(0,0))
	val epsPoint2 = JtsGeoFactory.createPoint(new Coordinate(3,4))
end TestGeometries

