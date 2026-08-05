package se.lu.nateko.cp.meta.api

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.BindingSet

trait SparqlRunner:
	def evaluateGraphQuery(query: String): CloseableIterator[Statement]
	def evaluateTupleQuery(query: String): CloseableIterator[BindingSet]
