package se.lu.nateko.cp.meta.services.sparql.magic.stats

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.ValueFactory

case class StatEntry(spec: IRI, submitter: IRI, station: Option[IRI], count: Int)

class StatsIndex(factory: ValueFactory) {

	def entries: Iterable[StatEntry] = Seq(
		StatEntry(iri("spec"), iri("subm"), Some(iri("station")), 42)
	)

	def add(st: Statement): Unit = {
	}

	private def iri(suff: String) = factory.createIRI("http://example.org/" + suff)
}