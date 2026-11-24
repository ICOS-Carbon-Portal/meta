package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery, SparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.*

class Rdf4jSparqlRunner(repo: Repository) extends SparqlRunner {

	def evaluateGraphQuery(q: SparqlQuery): CloseableIterator[Statement] = repo.access(
		_.prepareGraphQuery(QueryLanguage.SPARQL, q.query).evaluate()
	)

	def evaluateTupleQuery(q: SparqlQuery): CloseableIterator[BindingSet] = repo.access(
		_.prepareTupleQuery(QueryLanguage.SPARQL, q.query).evaluate()
	)
}
