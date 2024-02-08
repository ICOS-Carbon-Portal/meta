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

type ClusterID = Md5Sum //??

class GeoLookup(staticObjReader: StaticObjectReader)(using conn: GlobConn):
	val metaVocab = staticObjReader.metaVocab
	val reader = GeoJsonReader()

	val stationLatLons: Map[IRI, Geometry] =
		getStatements(null, metaVocab.hasStationId, null)
			.flatMap:
				case Rdf4jStatement(station, _, _) =>
					val ptV = staticObjReader.getLatLon(station).map(p =>
						val coordinate = new Coordinate(p.lon, p.lat)
						JtsGeoFactory.createPoint(coordinate))
					ptV.result.map(station -> _)
				case _ => None
			.toMap

	val coverages: Iterator[IRI] =
		getStatements(null, RDF.TYPE, metaVocab.latLonBoxClass)
			.collect:
				case Rdf4jStatement(cov, _, _) => cov

	val latLonBoxIds: Map[IRI, Md5Sum] =
		coverages
			.flatMap: cov =>
				getStatements(cov, metaVocab.asGeoJSON, null).collect:
					case Rdf4jStatement(_, _, geoJson) =>
						cov -> Md5Sum.ofStringBytes(geoJson.toString())
		.toMap

	val latLonBoxGeometries: Map[Md5Sum, Geometry] =
		coverages
			.flatMap: cov =>
				getStatements(cov, metaVocab.asGeoJSON, null).collect: // duplicate
					case Rdf4jStatement(_, _, geoJson) =>
						Md5Sum.ofStringBytes(geoJson.toString()) -> reader.read(geoJson.toString())
		.toMap

	val datasetTypes: Map[IRI, DatasetType] =
		getStatements(null, metaVocab.hasDataLevel, null)
			.flatMap:
				case Rdf4jStatement(spec, _, _) =>
					staticObjReader.getSpecDatasetType(spec).result.map(spec -> _)
				case _ => None
			.toMap
