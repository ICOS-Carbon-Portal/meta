package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.order.StatementOrder
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategy
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.impl.{DefaultEvaluationStrategy, DefaultEvaluationStrategyFactory, EvaluationStatistics}
import org.eclipse.rdf4j.query.algebra.evaluation.federation.{FederatedServiceResolver, FederatedServiceResolverClient}
import org.eclipse.rdf4j.sail.helpers.{NotifyingSailConnectionWrapper, NotifyingSailWrapper}
import org.eclipse.rdf4j.sail.{NotifyingSail, NotifyingSailConnection}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationProvider}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.{Failure, Success}

import se.lu.nateko.cp.meta.core.data.EnvriConfigs


type StoreSail = FederatedServiceResolverClient & NotifyingSail:
	def setEvaluationStrategyFactory(factory: EvaluationStrategyFactory): Unit


class CpNotifyingSail(
	inner: StoreSail,
	citer: CitationProvider
)(using EnvriConfigs) extends NotifyingSailWrapper(inner):

	private val log = LoggerFactory.getLogger(getClass())
	private val enricher = StatementsEnricher(citer)
	private var readonlyErrMessage: Option[String] = None

	override def getConnection(): NotifyingSailConnection =
		val innerConn = inner.getConnection()
		val enriched = CpNotifyingSailConnection(innerConn, enricher)
		readonlyErrMessage.fold(enriched)(ReadonlyConnectionWrapper(enriched, _))

	override def init(): Unit =
		inner.init()
		inner.setEvaluationStrategyFactory:
			CpEnrichingEvaluationStrategyFactory(inner.getFederatedServiceResolver(), enricher)

	def makeReadonly(errorMessage: String): Unit =
		readonlyErrMessage = Some(errorMessage)

	def makeReadonlyDumpIndexAndCaches(errorMessage: String)(using ExecutionContext): Future[String] =
		if readonlyErrMessage.isDefined then
			readonlyErrMessage = Some(errorMessage)
			Future.successful("Triple store already in read-only mode")
		else
			readonlyErrMessage = Some(errorMessage)
			val citClient = citer.doiCiter
			val citationsDump = CitationClient.writeCitCache(citClient)
			val doiMetaDump = CitationClient.writeDoiCache(citClient)
			Future.sequence(Seq(citationsDump, doiMetaDump)).map(_ =>
				"Switched the triple store to read-only mode. Citations cache dumped to disk"
			).andThen{
				case Success(msg) => log.info(msg)
				case Failure(err) => log.error("Fail while dumping citations cache to disk", err)
			}


end CpNotifyingSail


class CpEnrichingEvaluationStrategyFactory(
	fedResolver: FederatedServiceResolver,
	enricher: StatementsEnricher
) extends DefaultEvaluationStrategyFactory(fedResolver):

	override def createEvaluationStrategy(
		dataSet: Dataset, tripleSource: TripleSource, stats: EvaluationStatistics
	): EvaluationStrategy =
		val enriched = CpEnrichedTripleSource(tripleSource, enricher)
		DefaultEvaluationStrategy(enriched, dataSet, fedResolver, 0, stats)

end CpEnrichingEvaluationStrategyFactory


class CpNotifyingSailConnection(
	inner: NotifyingSailConnection,
	enricher: StatementsEnricher
) extends NotifyingSailConnectionWrapper(inner):

	override def getStatements(
		subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	): CloseableIteration[? <: Statement] =
		val base = inner.getStatements(subj, pred, obj, includeInferred, contexts*)
		enricher.enrich(base, subj, pred, obj)

	override def getStatements(
		statementOrder: StatementOrder, subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	): CloseableIteration[? <: Statement] =
		???

end CpNotifyingSailConnection
