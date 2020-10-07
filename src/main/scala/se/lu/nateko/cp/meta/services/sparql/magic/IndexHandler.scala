package se.lu.nateko.cp.meta.services.sparql.magic

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.sail.Sail

import akka.actor.Scheduler
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.async.throttle
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.memory.MemoryStore
import akka.event.NoLogging
import akka.event.LoggingAdapter

trait IndexProvider extends SailConnectionListener{
	def index: CpIndex
}

class IndexHandler(fromSail: Sail, scheduler: Scheduler, log: LoggingAdapter)(implicit ctxt: ExecutionContext) extends IndexProvider {

	val index = new CpIndex(fromSail)(log)
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

class DummyIndexProvider extends IndexProvider{
	val index = {
		val sail = new MemoryStore
		sail.initialize()
		new CpIndex(sail)(NoLogging)
	}
	def statementAdded(s: Statement): Unit = {}
	def statementRemoved(s: Statement): Unit = {}
}
