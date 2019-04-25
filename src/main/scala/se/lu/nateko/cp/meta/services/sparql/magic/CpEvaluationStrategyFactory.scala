package se.lu.nateko.cp.meta.services.sparql.magic


import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver
import org.eclipse.rdf4j.query.algebra.evaluation.impl.AbstractEvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.impl.TupleFunctionEvaluationStrategy
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryEvaluationException
import org.eclipse.rdf4j.query.algebra.evaluation.function.TupleFunctionRegistry
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DataObjectFetch
import se.lu.nateko.cp.meta.services.CpVocab
import scala.collection.JavaConverters.asJavaIterator
import se.lu.nateko.cp.meta.utils.rdf4j._


class CpEvaluationStrategyFactory(
	tupleFunctionReg: TupleFunctionRegistry,
	fedResolver: FederatedServiceResolver,
	indexThunk: () => CpIndex
) extends AbstractEvaluationStrategyFactory{

	override def createEvaluationStrategy(dataSet: Dataset, tripleSrc: TripleSource) =
		new TupleFunctionEvaluationStrategy(tripleSrc, dataSet, fedResolver, tupleFunctionReg){

			override def evaluate(expr: TupleExpr, bindings: BindingSet): CloseableIteration[BindingSet, QueryEvaluationException] = {
				expr match {
					case doFetch: DataObjectFetch =>
						new CloseableIteratorIteration(asJavaIterator(bindingsForObjectFetch(doFetch, bindings)))
					case _ =>
						super.evaluate(expr, bindings)
				}
			}
		}

	private def bindingsForObjectFetch(doFetch: DataObjectFetch, bindings: BindingSet): Iterator[BindingSet] = {

		val infos1: Iterator[ObjInfo] = bindings.getValue(doFetch.dobjVar) match {
			case null =>
				indexThunk().objInfo.valuesIterator
			case dobjUri @ CpVocab.DataObject(hash, _) =>
				indexThunk().objInfo.get(hash).filter(_.uri === dobjUri).iterator
			case _ =>
				Iterator.empty
		}

		val infos2 = if(doFetch.excludeDeprecated) infos1.filterNot(_.isDeprecated) else infos1

		val infos3 = doFetch.specVar.flatMap(spec => Option(bindings.getValue(spec))).fold(infos2){
			case spec: IRI =>
				infos2.filter(_.spec === spec)
			case _ =>
				infos2
		}

		infos3.map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			bs.setBinding(doFetch.dobjVar, oinfo.uri)
			doFetch.specVar.foreach(bs.setBinding(_, oinfo.spec))
			bs
		}.zipWithIndex.collect{
			case (bs, i) =>
				if(i < 3 || i == 10 || i == 100 || i == 1000 || i == 10000) println(s"binding $i : $bs")
				if(i == 3) println("...")
				bs
		}
	}
}
