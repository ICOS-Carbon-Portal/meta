package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.sail.nativerdf.NativeStoreConnection
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner
import se.lu.nateko.cp.meta.services.sparql.magic.stats.StatsQueryModelVisitor
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DataObjectFetchPatternSearch
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
import org.eclipse.rdf4j.sail.base.SailSource
import org.eclipse.rdf4j.sail.base.SailDataset
import org.eclipse.rdf4j.query.impl.EmptyBindingSet
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.sail.evaluation.SailTripleSource
import org.eclipse.rdf4j.sail.nativerdf.NativeStore
import se.lu.nateko.cp.meta.services.CitationProvider

class CpNativeStoreConnection(
	sail: NativeStore,
	citer: CitationProvider,
	naiveMode: Boolean = true
) extends NativeStoreConnection(sail){

	private val valueFactory = sail.getValueFactory
	private val metaVocab = new CpmetaVocab(valueFactory)

	override def evaluateInternal(
		expr: TupleExpr,
		dataset: Dataset,
		bindings: BindingSet,
		includeInferred: Boolean
	): CloseableIteration[_ <: BindingSet, QueryEvaluationException] = try{

		val tupleExpr: TupleExpr = TupleExprCloner.cloneExpr(expr)
		logger.info("Original query model:\n{}", tupleExpr)
		tupleExpr.visit(new StatsQueryModelVisitor)

		val dofps = new DataObjectFetchPatternSearch(metaVocab)
		dofps.search(tupleExpr).foreach(_.fuse())

		logger.info("Fused query model:\n{}", tupleExpr)

		if(naiveMode){
			val tripleSource = new SailTripleSource(this, includeInferred, valueFactory)
			val strategy = getEvaluationStrategy(dataset, tripleSource)
			strategy.evaluate(tupleExpr, EmptyBindingSet.getInstance)
		} else
			super.evaluateInternal(tupleExpr, dataset, bindings, includeInferred)
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
