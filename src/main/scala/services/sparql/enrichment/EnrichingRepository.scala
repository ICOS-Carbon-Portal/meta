package se.lu.nateko.cp.meta.services.sparql.enrichment

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Resource, Value, ValueFactory}
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver
import org.eclipse.rdf4j.query.algebra.evaluation.impl.{DefaultEvaluationStrategy, EvaluationStatistics}
import org.eclipse.rdf4j.query.algebra.evaluation.{QueryEvaluationStep, TripleSource}
import org.eclipse.rdf4j.query.algebra.{QueryRoot, StatementPattern, TupleExpr}
import org.eclipse.rdf4j.query.parser.QueryParserUtil
import org.eclipse.rdf4j.query.{BindingSet, Dataset, GraphQuery, Query, QueryLanguage, TupleQuery}
import org.eclipse.rdf4j.repository.base.{RepositoryConnectionWrapper, RepositoryWrapper}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sparql.federation.SPARQLServiceResolver
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import org.eclipse.rdf4j.sail.helpers.{AbstractSail, AbstractSailConnection}
import org.eclipse.rdf4j.sail.{SailConnection, SailException}
import se.lu.nateko.cp.meta.services.citation.CitationProvider

/**
 * Keeps normal SPARQL queries on the external repository, while evaluating queries that
 * can see computed citation statements locally. The local evaluator still reads every
 * stored statement from the external repository; it does not maintain an RDF store.
 */
final class EnrichingRepository(base: Repository, citer: CitationProvider)
	extends RepositoryWrapper(base) {

	private val computedPredicates = Set(
		citer.metaVocab.hasBiblioInfo,
		citer.metaVocab.hasCitationString,
		citer.metaVocab.dcterms.license
	)
	private val localRepo = SailRepository(RepositoryBackedSail(base, StatementsEnricher(citer)))
	localRepo.init()

	override def getConnection(): RepositoryConnection = {
		EnrichingRepositoryConnection(this, base.getConnection(), localRepo.getConnection(), computedPredicates)
	}

	override def shutDown(): Unit = {
		try { localRepo.shutDown() }
		finally { base.shutDown() }
	}
}


private final class EnrichingRepositoryConnection(
	repository: Repository,
	base: RepositoryConnection,
	local: RepositoryConnection,
	computedPredicates: Set[IRI]
) extends RepositoryConnectionWrapper(repository, base) {

	private def needsEnrichment(ql: QueryLanguage, query: String, baseUri: String): Boolean = {
		val parsed = QueryParserUtil.parseQuery(ql, query, baseUri)
		var result = false
		parsed.getTupleExpr.visitChildren(new org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor[RuntimeException] {
			override def meet(pattern: StatementPattern): Unit = {
				val predicate = pattern.getPredicateVar
				if (!predicate.hasValue || predicate.getValue.isInstanceOf[IRI] &&
					computedPredicates.contains(predicate.getValue.asInstanceOf[IRI])
				) result = true
			}
		})
		result
	}

	private def target(ql: QueryLanguage, query: String, baseUri: String): RepositoryConnection = {
		if (needsEnrichment(ql, query, baseUri)) local else base
	}

	override def prepareQuery(ql: QueryLanguage, query: String, baseUri: String): Query = {
		target(ql, query, baseUri).prepareQuery(ql, query, baseUri)
	}

	override def prepareTupleQuery(ql: QueryLanguage, query: String, baseUri: String): TupleQuery = {
		target(ql, query, baseUri).prepareTupleQuery(ql, query, baseUri)
	}

	override def prepareGraphQuery(ql: QueryLanguage, query: String, baseUri: String): GraphQuery = {
		target(ql, query, baseUri).prepareGraphQuery(ql, query, baseUri)
	}

	override def getStatements(
		subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	) = {
		local.getStatements(subj, pred, obj, includeInferred, contexts*)
	}

	override def close(): Unit = {
		try { local.close() }
		finally { super.close() }
	}
}


private final class RepositoryBackedSail(base: Repository, enricher: StatementsEnricher) extends AbstractSail {
	private val serviceResolver = SPARQLServiceResolver()

	override def getValueFactory(): ValueFactory = base.getValueFactory
	override def isWritable(): Boolean = false
	override protected def getConnectionInternal(): SailConnection = {
		RepositoryBackedSailConnection(this, base.getConnection(), enricher, serviceResolver)
	}
	override protected def shutDownInternal(): Unit = serviceResolver.shutDown()
}


private final class RepositoryBackedSailConnection(
	sail: RepositoryBackedSail,
	base: RepositoryConnection,
	enricher: StatementsEnricher,
	serviceResolver: FederatedServiceResolver
) extends AbstractSailConnection(sail) {

	private val tripleSource = new TripleSource {
		override def getValueFactory(): ValueFactory = base.getValueFactory
		override def getStatements(subj: Resource, pred: IRI, obj: Value, contexts: Resource*) = {
			enricher.enrich(base.getStatements(subj, pred, obj, false, contexts*), subj, pred, obj)
		}
	}

	override protected def evaluateInternal(
		tupleExpr: TupleExpr, dataset: Dataset, bindings: BindingSet, includeInferred: Boolean
	): CloseableIteration[? <: BindingSet] = {
		try {
			val root = tupleExpr.clone() match {
				case queryRoot: QueryRoot => queryRoot
				case expression => QueryRoot(expression)
			}
			val stats = EvaluationStatistics()
			val strategy = DefaultEvaluationStrategy(tripleSource, dataset, serviceResolver, 0, stats)
			val optimized = strategy.optimize(root, stats, bindings)
			val step: QueryEvaluationStep = strategy.precompile(optimized)
			step.evaluate(bindings)
		} catch {
			case err => throw SailException(err)
		}
	}

	override protected def getStatementsInternal(
		subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	) = {
		enricher.enrich(base.getStatements(subj, pred, obj, includeInferred, contexts*), subj, pred, obj)
	}

	override protected def getContextIDsInternal() = base.getContextIDs()
	override protected def sizeInternal(contexts: Resource*): Long = base.size(contexts*)
	override protected def getNamespacesInternal() = base.getNamespaces()
	override protected def getNamespaceInternal(prefix: String): String = base.getNamespace(prefix)

	override protected def closeInternal(): Unit = base.close()
	override protected def startTransactionInternal(): Unit = ()
	override protected def commitInternal(): Unit = ()
	override protected def rollbackInternal(): Unit = ()

	private def readonly(): Nothing = throw SailException("The enriching repository is read-only")
	override protected def addStatementInternal(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = readonly()
	override protected def removeStatementsInternal(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = readonly()
	override protected def clearInternal(contexts: Resource*): Unit = readonly()
	override protected def setNamespaceInternal(prefix: String, name: String): Unit = readonly()
	override protected def removeNamespaceInternal(prefix: String): Unit = readonly()
	override protected def clearNamespacesInternal(): Unit = readonly()
}
