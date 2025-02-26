package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.sail.Sail
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.instanceserver.StatementSource.getStatements
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.rdf4j.{Rdf4jStatement, accessEagerly}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

class GeoIndexProvider(using ExecutionContext):
	private val log = LoggerFactory.getLogger(getClass())

	def index(
		sail: Sail, cpIndex: CpIndex, staticObjReader: StaticObjectReader
	): Future[(GeoIndex, GeoEventProducer)] = Future:
		sail.accessEagerly:
			val geoLookup = GeoLookup(staticObjReader)
			val metaVocab = staticObjReader.metaVocab
			val events = GeoEventProducer(cpIndex, metaVocab, geoLookup)
			makeIndex(events, metaVocab) -> events

	private def makeIndex(events: GeoEventProducer, metaVocab: CpmetaVocab)(using GlobConn): GeoIndex =
		val dobjStIter = getStatements(null, RDF.TYPE, metaVocab.dataObjectClass)
		Using(dobjStIter): dobjSts =>
			val geo = new GeoIndex
			var objCounter = 0

			log.info("Starting to add data objects to geo index")
			dobjSts
				.collect:
					case Rdf4jStatement(dobj, _, _) => dobj
				.flatMap: dobj =>
					events.getDobjEvents(dobj).result.getOrElse(Seq.empty)
				.foreach: event =>
					geo.putQuickly(event)
					objCounter = objCounter + 1
					if objCounter % 500000 == 0 then log.info(s"Added $objCounter objects to the geo index...")

			geo.arrangeClusters()

			log.info("Geo index initialized with info on " + objCounter + " data objects")

			geo
		.get

end GeoIndexProvider
