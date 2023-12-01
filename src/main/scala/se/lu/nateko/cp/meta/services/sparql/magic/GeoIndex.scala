package se.lu.nateko.cp.meta.services.sparql.magic

import org.roaringbitmap.RoaringBitmap
import se.lu.nateko.cp.meta.core.data.LatLonBox

case class GeoEvent(
	objIdx: Int,
	isAssertion: Boolean,
	geoJSON: String,
	cluster: Option[String]
)

class GeoIndex:

	def put(event: GeoEvent): Unit = ???

	def filter(bbox: LatLonBox, otherFilter: Option[RoaringBitmap]): RoaringBitmap = ???

end GeoIndex
