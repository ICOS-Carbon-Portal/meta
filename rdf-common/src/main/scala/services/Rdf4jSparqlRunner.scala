package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.*

class Rdf4jSparqlRunner(repo: Repository) extends SparqlRunner {

	def evaluateGraphQuery(query: String): CloseableIterator[Statement] = repo.access(
		_.prepareGraphQuery(QueryLanguage.SPARQL, query).evaluate()
	)

	def evaluateTupleQuery(query: String): CloseableIterator[BindingSet] = repo.access(
		_.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate()
	)
}
