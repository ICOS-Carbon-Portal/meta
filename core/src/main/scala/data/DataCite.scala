package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.doi.meta.GeoLocation

object DataCite:

	def geosToCp(geos: Iterable[GeoLocation]): Option[GeoFeature] =
		Option(geos.flatMap(geoToCp).toSeq).filterNot(_.isEmpty).map: features =>
			FeatureCollection(features, None, None)

	def geoToCp(geo: GeoLocation): Option[GeoFeature] = (
		for
			box <- geo.geoLocationBox
			minLat <- box.southBoundLatitude
			minLon <- box.westBoundLongitude
			maxLat <- box.northBoundLatitude
			maxLon <- box.eastBoundLongitude
		yield
			LatLonBox(
				min = Position.ofLatLon(minLat, minLon),
				max = Position.ofLatLon(maxLat, maxLon),
				label = geo.geoLocationPlace,
				uri = None
			)
		).orElse:
			for
				p <- geo.geoLocationPoint
				lat <- p.pointLatitude
				lon <- p.pointLongitude
			yield
				Position(lat = lat, lon = lon, alt = None, label = geo.geoLocationPlace, uri = None)

end DataCite
