package se.lu.nateko.cp.meta.services.sparql.magic

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.sail.Sail

import akka.actor.Scheduler
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.async.throttle
import se.lu.nateko.cp.meta.services.sparql.magic.stats.StatsIndex
import org.eclipse.rdf4j.sail.SailConnectionListener

class IndexHandler(fromSail: Sail, scheduler: Scheduler)(implicit ctxt: ExecutionContext) extends SailConnectionListener {

	val index = new StatsIndex(fromSail)
	index.flush()

	private val flushIndex: () => Unit = throttle(() => index.flush(), 1.second, scheduler)

	def statementAdded(s: Statement): Unit = {
		index.put(RdfUpdate(s, true))
		flushIndex()
	}

	def statementRemoved(s: Statement): Unit = {
		index.put(RdfUpdate(s, false))
		flushIndex()
	}

}
