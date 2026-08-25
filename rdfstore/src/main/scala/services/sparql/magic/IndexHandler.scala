package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import akka.actor.Scheduler
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.sail.{Sail, SailConnectionListener}
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.async.throttle
import se.lu.nateko.cp.meta.utils.rdf4j.{===, Rdf4jStatement, accessEagerly}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class IndexHandler(scheduler: Scheduler)(using ExecutionContext):

	def getListener(
		sail: Sail,
		metaVocab: CpmetaVocab,
		index: CpIndex,
		geo: Future[(GeoIndex, GeoEventProducer)]
	) = new SailConnectionListener:
		//important that this is a val, not a def, otherwise throttle will work very wrongly
		private val flushIndex: () => Unit = throttle(() => index.flush(), 1.second, scheduler)

		def statementAdded(s: Statement): Unit =
			index.put(RdfUpdate(s, true))
			flushIndex()
			s match
				case Rdf4jStatement(dobj, pred, _) if pred === metaVocab.hasSizeInBytes =>
					geo.onComplete:
						case Success((geoIndex, events)) =>
							sail.accessEagerly:
								events.getDobjEvents(dobj).foreach: evs =>
									evs.foreach(geoIndex.put)
						case _ =>
				case _ =>

		def statementRemoved(s: Statement): Unit =
			index.put(RdfUpdate(s, false))
			flushIndex()


end IndexHandler
