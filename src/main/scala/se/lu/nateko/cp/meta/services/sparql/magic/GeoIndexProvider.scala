package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sail.Sail
import org.locationtech.jts.geom.GeometryFactory
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.data.DatasetType
import se.lu.nateko.cp.meta.instanceserver.Rdf4jSailConnection
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Using

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
		val geoLookup = GeoLookup(staticObjReader)
		val event = GeoEventProducer(staticObjReader)

		def getStation(dobj: IRI): Validated[IRI] =
			for
				acq <- getSingleUri(dobj, metaVocab.wasAcquiredBy)
				stationUri <- getSingleUri(acq, metaVocab.prov.wasAssociatedWith)
			yield stationUri

		def getStationClusterId(dobj: IRI): Option[String] =
			val stationV = for
				spec <- getSingleUri(dobj, metaVocab.hasObjectSpec)
					if geoLookup.datasetTypes.get(spec) == Some(DatasetType.StationTimeSeries)
				station <- getStation(dobj)
			yield station.toString
			stationV.result

		val geoEvents: Iterator[GeoEvent] = getStatements(null, RDF.TYPE, metaVocab.dataObjectClass)
			.collect:
				case Rdf4jStatement(dobj, _, _) => dobj
			.flatMap: dobj =>
				getHashsum(dobj, metaVocab.hasSha256sum).flatMap: objHash =>
					val idx = cpIndex.getObjEntry(objHash).idx
					val stationClusterId = getStationClusterId(dobj)

					getSingleUri(dobj, metaVocab.hasSpatialCoverage).flatMap: cov =>
						getSingleString(cov, metaVocab.asGeoJSON).map: geoJson =>
							event.ofOwnGeoJson(idx, geoJson, stationClusterId)
						.or:
							Validated(event.ofLatLonBox(idx, cov, stationClusterId))
					.or:
						getSingleUri(dobj, metaVocab.wasAcquiredBy).flatMap: acq =>
							getSingleUri(acq, metaVocab.hasSamplingPoint).flatMap: samplingPt =>
								event.ofSamplingPt(idx, acq, stationClusterId)
							.or:
								for
									site <- getSingleUri(acq, metaVocab.wasPerformedAt)
									cov <- getSingleUri(site, metaVocab.hasSpatialCoverage)
									siteGeoJson <- getSingleString(cov, metaVocab.asGeoJSON)
								yield
									val siteClusterId = site.toString()
									event.ofOwnGeoJson(idx, siteGeoJson, Some(siteClusterId))
							.or:
								getSingleUri(acq, metaVocab.prov.wasAssociatedWith).map: station =>
									event.ofStationPt(idx, station, stationClusterId)
				.result.toSeq.flatten

		var objCounter = 0

		val startTime = System.currentTimeMillis()
		geoEvents.foreach: event =>
			if objCounter % 100000 == 0 then println(s"Object nbr $objCounter being processed")
			geo.putQuickly(event)
			objCounter = objCounter + 1

		val endTime = System.currentTimeMillis()
		println(s"Put took ${endTime - startTime} ms")

		geo.arrangeClusters()
		println("arranged clusters")
		val endOfAll = System.currentTimeMillis()

		println(s"Everything took ${endOfAll - startTime} ms")
		println("Total number of objects: " + objCounter)

		geo

end GeoIndexProvider

