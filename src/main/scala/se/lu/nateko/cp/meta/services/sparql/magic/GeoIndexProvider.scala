package se.lu.nateko.cp.meta.services.sparql.magic

import akka.event.LoggingAdapter
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sail.Sail
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.instanceserver.Rdf4jSailConnection
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.*
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.Using

class GeoIndexProvider(log: LoggingAdapter)(using ExecutionContext):

	def apply(
		sail: Sail, cpIndex: CpIndex, staticObjReader: StaticObjectReader
	): Future[(GeoIndex, GeoEventProducer)] = Future:
		Future.fromTry:
			Using(sail.getConnection): conn =>
				val sailConn = Rdf4jSailConnection(null, Nil, conn, sail.getValueFactory)
				given GlobConn = RdfLens.global(using sailConn)
				val geoLookup = GeoLookup(staticObjReader)
				val events = GeoEventProducer(cpIndex, staticObjReader, geoLookup)
				makeIndex(events).map(_ -> events)
			.flatten
	.flatten

	private def makeIndex(events: GeoEventProducer)(using GlobConn): Try[GeoIndex] =
		val dobjStIter = getStatements(null, RDF.TYPE, events.metaVocab.dataObjectClass)
		Using(dobjStIter): dobjSts =>
			val geo = new GeoIndex
			var objCounter = 0

			dobjSts
				.collect:
					case Rdf4jStatement(dobj, _, _) => dobj
				.flatMap: dobj =>
					events.getDobjEvents(dobj).result.getOrElse(Seq.empty)
				.foreach: event =>
					if objCounter % 500000 == 0 then log.info(s"Added $objCounter objects to the geo index...")
					geo.putQuickly(event)
					objCounter = objCounter + 1

			geo.arrangeClusters()

			log.info("Geo index initialized with info on " + objCounter + " data objects")

			geo

end GeoIndexProvider
