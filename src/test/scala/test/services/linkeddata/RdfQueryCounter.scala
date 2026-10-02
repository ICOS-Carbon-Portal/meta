package se.lu.nateko.cp.meta.test.services.linkeddata

import scala.language.unsafeNulls

import java.util.concurrent.atomic.AtomicInteger

import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.query.{QueryLanguage, TupleQuery}
import org.eclipse.rdf4j.repository.base.{RepositoryConnectionWrapper, RepositoryWrapper}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection, RepositoryResult}

case class QueryCounts(connections: Int, statements: Int, existence: Int, sparql: Int)

/** Counts reads made through an RDF4J repository, per query kind. */
private final class QueryCounter {
	private val connections = AtomicInteger()
	private val statements = AtomicInteger()
	private val existence = AtomicInteger()
	private val sparql = AtomicInteger()

	def countConnection(): Unit = connections.incrementAndGet()
	def countStatements(): Unit = statements.incrementAndGet()
	def countExistence(): Unit = existence.incrementAndGet()
	def countSparql(): Unit = sparql.incrementAndGet()

	def snapshot: QueryCounts =
		QueryCounts(connections.get, statements.get, existence.get, sparql.get)
}

final class CountingRepository(delegate: Repository) extends RepositoryWrapper(delegate) {
	private val counter = QueryCounter()

	/** The reads made through this repository so far. */
	def counts: QueryCounts = counter.snapshot

	override def getConnection(): RepositoryConnection = {
		counter.countConnection()
		CountingConnection(this, super.getConnection(), counter)
	}
}

private final class CountingConnection(
	repo: Repository, delegate: RepositoryConnection, counter: QueryCounter
) extends RepositoryConnectionWrapper(repo, delegate) {

	override def getStatements(
		subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	): RepositoryResult[org.eclipse.rdf4j.model.Statement] = {
		counter.countStatements()
		super.getStatements(subj, pred, obj, includeInferred, contexts*)
	}

	override def hasStatement(
		subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	): Boolean = {
		counter.countExistence()
		super.hasStatement(subj, pred, obj, includeInferred, contexts*)
	}

	override def prepareTupleQuery(ql: QueryLanguage, query: String, baseURI: String): TupleQuery = {
		counter.countSparql()
		super.prepareTupleQuery(ql, query, baseURI)
	}
}
