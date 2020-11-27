package org.eclipse.rdf4j.sail.nativerdf

import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DofPatternFusion
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DofPatternRewrite
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DofPatternSearch
import se.lu.nateko.cp.meta.utils.rdf4j._
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.model._
import org.eclipse.rdf4j.sail.SailException
import org.eclipse.rdf4j.common.iteration.SingletonIteration
import org.eclipse.rdf4j.common.iteration.UnionIteration
import org.eclipse.rdf4j.query.impl.EmptyBindingSet
import org.eclipse.rdf4j.sail.evaluation.SailTripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.impl._
import scala.util.Try

class CpNativeStoreConnection(
	sail: NativeStore,
	citer: CitationProvider
) extends NativeStoreConnection(sail){

	private val valueFactory = sail.getValueFactory
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

		def enrich(inner: StatIter, pred: IRI, vTry: => Option[String]): StatIter = Try(vTry).getOrElse(None).fold(inner){v =>
			val extras: StatIter = new SingletonIteration(
				valueFactory.createStatement(subj, pred, valueFactory.createStringLiteral(v))
			)
			new UnionIteration(inner, extras)
		}

		val base: StatIter = super
			.getStatementsInternal(subj, pred, obj, includeInferred, contexts: _*)
			.asInstanceOf[StatIter]

		if(
			subj == null || obj != null || //limited functionality for now
			(pred != null && !Set(metaVocab.hasBiblioInfo, metaVocab.hasCitationString).contains(pred))
		)
			base
		else{

			val predsMap: Map[IRI, Function0[Option[String]]] = Map(
				metaVocab.hasCitationString -> (() => citer.getCitation(subj)),
				metaVocab.hasBiblioInfo -> (() => {
					import se.lu.nateko.cp.meta.core.data.JsonSupport.referencesFormat
					import spray.json._
					citer.getReferences(subj).map(_.toJson.compactPrint)
				})
			)
			if(pred == null) predsMap.foldLeft(base){
				case (iter, (pred, thunk)) => enrich(iter, pred, thunk())
			}
			else predsMap.get(pred).fold(base){thunk =>
				enrich(base, pred, thunk())
			}

		}
	}

}
