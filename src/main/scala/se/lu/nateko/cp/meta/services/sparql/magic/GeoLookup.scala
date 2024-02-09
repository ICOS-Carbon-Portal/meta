package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.IRI
import org.locationtech.jts.geom.Point
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import org.locationtech.jts.geom.Coordinate
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.DatasetType
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.meta.core.crypto.Md5Sum
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.geojson.GeoJsonReader
import se.lu.nateko.cp.meta.utils.Validated
import org.locationtech.jts.geom.GeometryCollection
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.GeoJson


class GeoLookup(staticObjReader: StaticObjectReader)(using conn: GlobConn):
	val metaVocab = staticObjReader.metaVocab
	val reader = GeoJsonReader()

	def geoFeatureToJtsGeometry(gf: GeoFeature): Geometry =
		val jsonStr = GeoJson.fromFeature(gf).toString
		reader.read(jsonStr)

	def getClusterId(geom: Geometry): String =
		Md5Sum.ofStringBytes(geom.toString()).toString

	val stationLatLons: Map[IRI, Geometry] =
		getStatements(null, metaVocab.hasStationId, null)
			.flatMap:
				case Rdf4jStatement(station, _, _) =>
					staticObjReader.getLatLon(station).map(p =>
						val coordinate = new Coordinate(p.lon, p.lat)
						JtsGeoFactory.createPoint(coordinate))
					.or:
						getSingleUri(station, metaVocab.hasSpatialCoverage).flatMap: cov =>
							getSingleString(cov, metaVocab.asGeoJSON).map: geoJson =>
								reader.read(geoJson)
					.or:
						getSingleUri(station, metaVocab.hasSpatialCoverage).flatMap: cov =>
							staticObjReader.getLatLonBox(cov).map: box =>
								geoFeatureToJtsGeometry(box)

					.result.map(station -> _)
				case _ => None
			.toMap

	val latLonBoxIds: Map[IRI, Md5Sum] =
		getStatements(null, RDF.TYPE, metaVocab.latLonBoxClass)
			.collect:
				case Rdf4jStatement(cov, _, _) => 
					cov -> Md5Sum.ofStringBytes(cov.toString())
		.toMap

	val latLonBoxGeometries: Map[Md5Sum, Geometry] =
		getStatements(null, RDF.TYPE, metaVocab.latLonBoxClass)
			.collect:
				case Rdf4jStatement(cov, _, _) => cov
			.flatMap: cov =>
				val clusterId = latLonBoxIds.get(cov)
				staticObjReader.getLatLonBox(cov).map: bbox =>
					val jtsBbox = geoFeatureToJtsGeometry(bbox)
					clusterId.map(_ -> jtsBbox)
				.or:
					getSingleString(cov, metaVocab.asGeoJSON).map: geoJson =>
						clusterId.map(_ -> reader.read(geoJson))
				.result.flatten
		.toMap

	val datasetTypes: Map[IRI, DatasetType] =
		getStatements(null, metaVocab.hasDataLevel, null)
			.flatMap:
				case Rdf4jStatement(spec, _, _) =>
					staticObjReader.getSpecDatasetType(spec).result.map(spec -> _)
				case _ => None
			.toMap
