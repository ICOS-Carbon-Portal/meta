package se.lu.nateko.cp.meta.services.sparql.magic.stats

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.sail.Sail

import akka.actor.Scheduler
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.sparql.magic.MagicTupleFuncPlugin
import se.lu.nateko.cp.meta.utils.async.throttle

class StatsPlugin(scheduler: Scheduler)(implicit ctxt: ExecutionContext) extends MagicTupleFuncPlugin {

	private var index: StatsIndex = _

	private val flushIndex: () => Unit = throttle(() => index.flush(), 1.second, scheduler)

	override def expressionEnricher = new StatsQueryModelVisitor

	override def initialize(fromSail: Sail): Unit = {
		index = new StatsIndex(fromSail)
		index.flush()
	}

	override def makeFunctions = Seq(
		new StatsTupleFunction(() => index)
	)

	def statementAdded(s: Statement): Unit = {
		index.put(RdfUpdate(s, true))
		flushIndex()
	}

	def statementRemoved(s: Statement): Unit = {
		index.put(RdfUpdate(s, false))
		flushIndex()
	}

}
