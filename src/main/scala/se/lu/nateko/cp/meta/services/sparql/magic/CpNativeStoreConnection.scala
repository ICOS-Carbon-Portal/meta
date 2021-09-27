package org.eclipse.rdf4j.sail.nativerdf

import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.common.iteration.SingletonIteration
import org.eclipse.rdf4j.common.iteration.UnionIteration
import org.eclipse.rdf4j.model._
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.algebra.evaluation.impl._
import org.eclipse.rdf4j.query.impl.EmptyBindingSet
import org.eclipse.rdf4j.sail.SailException
import org.eclipse.rdf4j.sail.evaluation.SailTripleSource
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DofPatternFusion
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DofPatternRewrite
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DofPatternSearch
import se.lu.nateko.cp.meta.utils.rdf4j._

import scala.util.Try

class CpNativeStoreConnection(
	sail: NativeStore,
	citer: CitationProvider
) extends NativeStoreConnection(sail){

	private implicit val valueFactory = sail.getValueFactory
	private val metaVocab = new CpmetaVocab(valueFactory)
	private val sailStore = sail.getSailStore

	override def evaluateInternal(
		expr: TupleExpr,
		dataset: Dataset,
		bindings: BindingSet,
		includeInferred: Boolean
	): CloseableIteration[_ <: BindingSet, QueryEvaluationException] = try{

		logger.debug("Original query model:\n{}", expr)

		val queryExpr: TupleExpr = TupleExprCloner.cloneExpr(expr)
		val dofps = new DofPatternSearch(metaVocab)
		val fuser = new DofPatternFusion(metaVocab)

		val pattern = dofps.find(queryExpr)
		val fusions = fuser.findFusions(pattern)
		DofPatternRewrite.rewrite(queryExpr, fusions)

		logger.debug("Fused query model:\n{}", queryExpr)

		flush()
		val tripleSource = new SailTripleSource(this, includeInferred, valueFactory)
		val strategy = getEvaluationStrategy(dataset, tripleSource)

		Seq( //taken from SailSourceConnection.evaluateInternal
			new BindingAssigner,
			new ConstantOptimizer(strategy),
			new CompareOptimizer(),
			new ConjunctiveConstraintSplitter(),
			new DisjunctiveConstraintOptimizer(),
			new SameTermFilterOptimizer(),
			new QueryModelNormalizer(),
			new QueryJoinOptimizer(sailStore.getEvaluationStatistics()),
			new IterativeEvaluationOptimizer(),
			new FilterOptimizer()
			//new OrderLimitOptimizer()
		).foreach(_.optimize(queryExpr, dataset, bindings))

		logger.debug("Fully optimized final query model:\n{}", queryExpr)

		strategy.evaluate(queryExpr, EmptyBindingSet.getInstance)
	}
	catch {
		case iae: IllegalArgumentException =>
			iae.printStackTrace()
			throw iae
	}

	override def getStatementsInternal(
		subj: Resource, pred: IRI, obj: Value,
		includeInferred: Boolean, contexts: Resource*
	): CloseableIteration[_ <: Statement, SailException] = {

		type StatIter = CloseableIteration[Statement, SailException]

		def enrich(inner: StatIter, pred: IRI, vTry: => Option[Value]): StatIter = Try(vTry).getOrElse(None).fold(inner){v =>
			val extras: StatIter = new SingletonIteration(
				valueFactory.createStatement(subj, pred, v)
			)
			new UnionIteration(inner, extras)
		}

		val base: StatIter = super
			.getStatementsInternal(subj, pred, obj, includeInferred, contexts: _*)
			.asInstanceOf[StatIter]

		if(
			subj == null || obj != null || //limited functionality for now
			(pred != null && !magicPredValueFactories.contains(pred))
		)
			base
		else if(pred == null) magicPredValueFactories.foldLeft(base){
			case (iter, (pred, thunk)) => enrich(iter, pred, thunk(subj))
		}
		else magicPredValueFactories.get(pred).fold(base){thunk =>
			enrich(base, pred, thunk(subj))
		}
	}

	private val magicPredValueFactories: Map[IRI, Resource => Option[Value]] = Map(
		metaVocab.hasCitationString -> (
			subj => citer.getCitation(subj).map(valueFactory.createStringLiteral)
		),
		metaVocab.hasBiblioInfo -> (subj => {
			import se.lu.nateko.cp.meta.core.data.JsonSupport.referencesFormat
			import spray.json._
			citer.getReferences(subj).map(js => valueFactory.createStringLiteral(js.toJson.compactPrint))
		}),
		metaVocab.dcterms.license -> (subj => citer.getLicence(subj).map(_.url.toRdf))
	)

}
