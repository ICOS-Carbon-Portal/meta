package se.lu.nateko.cp.meta.services.sparql.magic.stats

import se.lu.nateko.cp.meta.services.sparql.magic.MagicTupleFuncPlugin
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.IRI

//TODO Throttle index flushing ?
class StatsPlugin extends MagicTupleFuncPlugin {

	private var index: StatsIndex = _

	override def expressionEnricher = new StatsQueryModelVisitor

	override def initialize(fromSail: Sail): Unit = {
		index = new StatsIndex(fromSail)
		index.flush()
	}

	override def makeFunctions = Seq(
		new StatsTupleFunction(() => index)
	)

	def statementAdded(s: Statement): Unit = {
		index.add(s)
		index.flush()
	}

	def statementRemoved(s: Statement): Unit = {
		index.remove(s)
		index.flush()
	}

}
