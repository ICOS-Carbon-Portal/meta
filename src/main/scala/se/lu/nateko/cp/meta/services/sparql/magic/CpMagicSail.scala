package se.lu.nateko.cp.meta.services.sparql.magic

import akka.event.LoggingAdapter

import scala.language.reflectiveCalls

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunction
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.query.algebra.evaluation.impl.AbstractEvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.impl.TupleFunctionEvaluationStrategy
import org.eclipse.rdf4j.sail.NotifyingSailConnection
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.helpers.NotifyingSailConnectionWrapper
import org.eclipse.rdf4j.sail.helpers.NotifyingSailWrapper
import org.eclipse.rdf4j.query.algebra.QueryModelVisitor
import org.eclipse.rdf4j.sail.SailException

import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DataObjectFetchPatternSearch
import se.lu.nateko.cp.meta.services.sparql.magic.stats._
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.api.CitationClient
import se.lu.nateko.cp.meta.api.Doi
import se.lu.nateko.cp.meta.instanceserver.Rdf4jSailInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CitationProviderFactory
import se.lu.nateko.cp.meta.services.CitationProvider
import se.lu.nateko.cp.meta.services.upload.CollectionFetcherLite
import se.lu.nateko.cp.meta.services.upload.StaticObjectFetcher
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.views.LandingPageHelpers
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.utils.rdf4j._
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.common.iteration.UnionIteration
import org.eclipse.rdf4j.common.iteration.SingletonIteration

class CpMagicSail(
	baseSail: NativeOrMemoryStore,
	init: Sail => IndexHandler,
	citationFactory: CitationProviderFactory,
	log: LoggingAdapter
) extends NotifyingSailWrapper(baseSail){

	private var indexh: IndexHandler = _
	private var citer: CitationProvider = _

	baseSail.setEvaluationStrategyFactory{
		val tupleFunctionReg = new TupleFunctionRegistry()
		val indexThunk = () => indexh.index
		tupleFunctionReg.add(new StatsTupleFunction(indexThunk))
		new CpEvaluationStrategyFactory(tupleFunctionReg, baseSail.getFederatedServiceResolver, indexThunk)
	}

	def getCitationClient: CitationClient = citer.dataCiter

	override def initialize(): Unit = {
		log.info("Initializing triple store...")
		super.initialize()
		log.info("Triple store initialized, initializing Carbon Portal index...")
		indexh = init(baseSail)
		log.info(s"Carbon Portal index initialized with info on ${indexh.index.objInfo.size} data objects")
		citer = citationFactory.getProvider(baseSail)
		log.info("Initialized citation provider")
	}

	override def getConnection(): NotifyingSailConnection = new CpConnection(baseSail.getConnection)

	private class CpConnection(baseConn: NotifyingSailConnection) extends NotifyingSailConnectionWrapper(baseConn){

		getWrappedConnection.addConnectionListener(indexh)

		private val valueFactory = baseSail.getValueFactory
		private val metaVocab = new CpmetaVocab(valueFactory)

		override def evaluate(
			tupleExpr: TupleExpr,
			dataset: Dataset,
			bindings: BindingSet,
			includeInferred: Boolean
		): CloseableIteration[_ <: BindingSet, QueryEvaluationException] = {

			val expr: TupleExpr = TupleExprCloner.cloneExpr(tupleExpr)
			expr.visit(new StatsQueryModelVisitor)

			// val dofps = new DataObjectFetchPatternSearch(metaVocab)
			// dofps.search(expr).foreach(_.fuse())

			try{
				getWrappedConnection.evaluate(expr, dataset, bindings, includeInferred)
			} catch{
				case iae: IllegalArgumentException =>
					iae.printStackTrace()
					throw iae
			}
		}

		override def getStatements(
			subj: Resource, pred: IRI, obj: Value,
			includeInferred: Boolean, contexts: Resource*
		): CloseableIteration[_ <: Statement, SailException] = {

			val base: CloseableIteration[Statement, SailException] = getWrappedConnection
				.getStatements(subj, pred, obj, includeInferred, contexts: _*)
				.asInstanceOf[CloseableIteration[Statement, SailException]]

			if(subj == null || pred != null && pred != metaVocab.hasCitationString || obj != null) base else { //limited functionality for now

				citer.getCitation(subj).fold(base){citation =>
					val citations: CloseableIteration[Statement, SailException] = new SingletonIteration(
						valueFactory.createStatement(
							subj,
							metaVocab.hasCitationString,
							valueFactory.createStringLiteral(citation)
						)
					)
					new UnionIteration(base, citations)
				}
			}

		}
	}

}
