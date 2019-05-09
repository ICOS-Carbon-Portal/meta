package se.lu.nateko.cp.meta.services.sparql.magic


import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
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

		val specBinding: Option[Value] = doFetch.specVar.flatMap(spec => Option(bindings.getValue(spec)))

		val infos1: Iterator[ObjInfo] = bindings.getValue(doFetch.dobjVar) match {
			case null =>
				specBinding.fold(indexThunk().objInfo.valuesIterator){
					case spec: IRI =>
						indexThunk().getObjsForSpec(spec)
					case _ => Iterator.empty
				}
			case dobjUri @ CpVocab.DataObject(hash, _) =>
				indexThunk().objInfo.get(hash).filter(
					oinfo => oinfo.uri === dobjUri && specBinding.forall(oinfo.spec == _)
				).iterator
			case _ =>
				Iterator.empty
		}

		val infos2 = if(doFetch.excludeDeprecated) infos1.filterNot(_.isDeprecated) else infos1

		val infos3 = doFetch.stationVar.fold(infos2){stationVar =>
			val stationBinding: Option[Value] = Option(bindings.getValue(stationVar))
			stationBinding.fold(
				infos2.filter(_.station != null) //station variable needs to be set, but has not been set yet
			){
				station => infos2.filter(station == _.station)
			}
		}

		infos3.map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			bs.setBinding(doFetch.dobjVar, oinfo.uri)
			doFetch.specVar.foreach(bs.setBinding(_, oinfo.spec))
			doFetch.stationVar.foreach(bs.setBinding(_, oinfo.station))
			for(name <- doFetch.dataStartTimeVar; value <- oinfo.dataStartTime) bs.setBinding(name, value)
			for(name <- doFetch.dataEndTimeVar; value <- oinfo.dataEndTime) bs.setBinding(name, value)
			for(name <- doFetch.submStartTimeVar; value <- oinfo.submissionStartTime) bs.setBinding(name, value)
			for(name <- doFetch.submEndTimeVar; value <- oinfo.submissionEndTime) bs.setBinding(name, value)
			bs
		}
		// .zipWithIndex.collect{
		// 	case (bs, i) =>
		// 		if(i < 3 || i == 10 || i == 100 || i == 1000 || i == 10000) println(s"binding $i : $bs")
		// 		if(i == 3) println("...")
		// 		bs
		// }
	}
}
