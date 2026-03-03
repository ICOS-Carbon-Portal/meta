package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import akka.Done
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.order.StatementOrder
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverClient
import org.eclipse.rdf4j.sail.helpers.{NotifyingSailConnectionWrapper, NotifyingSailWrapper}
import org.eclipse.rdf4j.sail.{NotifyingSail, NotifyingSailConnection, SailConnectionListener}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.services.citation.{CitationClient, CitationProvider}
import se.lu.nateko.cp.meta.utils.async.ok

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.{Failure, Success}

import index.IndexData
import se.lu.nateko.cp.meta.core.data.EnvriConfigs


type MainSail = FederatedServiceResolverClient & NotifyingSail:
	def setEvaluationStrategyFactory(factory: EvaluationStrategyFactory): Unit


class CpNotifyingSail(
	inner: MainSail,
	indexFactories: Option[(IndexHandler, GeoIndexProvider)],
	citer: CitationProvider,
	excludedContexts: Seq[IRI] = Nil
)(using EnvriConfigs) extends NotifyingSailWrapper(inner):

	private val log = LoggerFactory.getLogger(getClass())
	private val enricher = StatementsEnricher(citer)
	private var cpIndex: Option[CpIndex] = None
	private var listener: Option[SailConnectionListener] = None
	private var readonlyErrMessage: Option[String] = None

	import citer.{metaVocab, metaReader}

	override def getConnection(): NotifyingSailConnection =
		val innerConn = inner.getConnection()
		val enriched = CpNotifyingSailConnection(innerConn, enricher, excludedContexts)
		listener.foreach(enriched.addConnectionListener)
		readonlyErrMessage.fold(enriched)(ReadonlyConnectionWrapper(enriched, _))

	override def init(): Unit =
		inner.init()
		setupQueryEvaluation()

	def initSparqlMagicIndex(idxData: Option[IndexData]): Future[Done] = indexFactories match
		case None =>
			log.info("Magic index is disabled")
			ok
		case Some((listenerFactory, geoFactory)) =>
			if idxData.isEmpty then log.info("Initializing Carbon Portal index...")
			val geoPromise = Promise[(GeoIndex, GeoEventProducer)]()
			val geoFut = geoPromise.future.map(_._1)(ExecutionContext.parasitic)
			val idx = idxData.fold(new CpIndex(inner, geoFut))(idx => new CpIndex(inner, geoFut, idx))
			idx.flush()
			listener = Some(listenerFactory.getListener(inner, metaVocab, idx, geoPromise.future))
			geoPromise.completeWith(geoFactory.index(inner, idx, metaReader))
			if(idxData.isEmpty) log.info(s"Carbon Portal index initialized with info on ${idx.size} data objects")
			cpIndex = Some(idx)
			setupQueryEvaluation()
			geoFut.map(_ => Done)(using ExecutionContext.parasitic)

	def makeReadonly(errorMessage: String): Unit =
		readonlyErrMessage = Some(errorMessage)

	def makeReadonlyDumpIndexAndCaches(errorMessage: String)(using ExecutionContext): Future[String] =
		if readonlyErrMessage.isDefined then
			readonlyErrMessage = Some(errorMessage)
			Future.successful("Triple store already in read-only mode")
		else
			readonlyErrMessage = Some(errorMessage)
			val indexDump = cpIndex.fold(ok){idx =>
				idx.flush()
				IndexHandler.store(idx)
			}
			val citClient = citer.doiCiter
			val citationsDump = CitationClient.writeCitCache(citClient)
			val doiMetaDump = CitationClient.writeDoiCache(citClient)
			Future.sequence(Seq(indexDump, citationsDump, doiMetaDump)).map(_ =>
				"Switched the triple store to read-only mode. SPARQL index and citations cache dumped to disk"
			).andThen{
				case Success(msg) => log.info(msg)
				case Failure(err) => log.error("Fail while dumping SPARQL index or citations cache to disk", err)
			}

	private def setupQueryEvaluation(): Unit =
		val magicIdx = cpIndex.getOrElse:
			CpIndex(inner, Future.never, IndexData(0)())
		inner.setEvaluationStrategyFactory:
			CpEvaluationStrategyFactory(inner.getFederatedServiceResolver(), magicIdx, enricher, cpIndex.isDefined)


end CpNotifyingSail


class CpNotifyingSailConnection(
	inner: NotifyingSailConnection,
	enricher: StatementsEnricher,
	excludedContexts: Seq[IRI] = Nil
) extends NotifyingSailConnectionWrapper(inner):

	override def getStatements(
		subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	): CloseableIteration[? <: Statement] =
		val base = inner.getStatements(subj, pred, obj, includeInferred, contexts*)
		val enriched = enricher.enrich(base, subj, pred, obj)
		if contexts.isEmpty && excludedContexts.nonEmpty then
			FilteredIteration(enriched, excludedContexts)
		else enriched

	override def getStatements(
		statementOrder: StatementOrder, subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*
	): CloseableIteration[? <: Statement] =
		???

end CpNotifyingSailConnection

private class FilteredIteration(
	inner: CloseableIteration[? <: Statement],
	excludedContexts: Seq[IRI]
) extends CloseableIteration[Statement]:
	private var nextStmt: Statement = null
	advance()

	private def advance(): Unit =
		nextStmt = null
		while nextStmt == null && inner.hasNext do
			val st = inner.next()
			if !excludedContexts.contains(st.getContext) then
				nextStmt = st

	override def hasNext: Boolean = nextStmt != null
	override def next(): Statement =
		if nextStmt == null then throw new java.util.NoSuchElementException()
		val res = nextStmt
		advance()
		res
	override def close(): Unit = inner.close()
