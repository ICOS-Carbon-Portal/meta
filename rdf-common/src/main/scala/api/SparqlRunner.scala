package se.lu.nateko.cp.meta.api

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.BindingSet

case class SparqlQuery(query: String)

trait SparqlRunner:
	def evaluateGraphQuery(q: SparqlQuery): CloseableIterator[Statement]
	def evaluateTupleQuery(q: SparqlQuery): CloseableIterator[BindingSet]
