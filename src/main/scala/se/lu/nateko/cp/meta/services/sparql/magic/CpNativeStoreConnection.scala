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

		val base: CloseableIteration[Statement, SailException] = super
			.getStatementsInternal(subj, pred, obj, includeInferred, contexts: _*)
			.asInstanceOf[CloseableIteration[Statement, SailException]]

		if(subj == null || pred != null && pred != metaVocab.hasCitationString || obj != null) base else { //limited functionality for now

			Try(citer.getCitation(subj)).getOrElse(None).fold(base){citation =>
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
