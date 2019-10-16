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
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.DataObjectFetchNode
import se.lu.nateko.cp.meta.services.CpVocab
import scala.collection.JavaConverters.asJavaIterator
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch


class CpEvaluationStrategyFactory(
	tupleFunctionReg: TupleFunctionRegistry,
	fedResolver: FederatedServiceResolver,
	indexThunk: () => CpIndex
) extends AbstractEvaluationStrategyFactory{

	override def createEvaluationStrategy(dataSet: Dataset, tripleSrc: TripleSource) =
		new TupleFunctionEvaluationStrategy(tripleSrc, dataSet, fedResolver, tupleFunctionReg){

			override def evaluate(expr: TupleExpr, bindings: BindingSet): CloseableIteration[BindingSet, QueryEvaluationException] = {
				expr match {
					case doFetch: DataObjectFetchNode =>
						new CloseableIteratorIteration(asJavaIterator(bindingsForObjectFetch(doFetch, bindings)))
					case _ =>
						super.evaluate(expr, bindings)
				}
			}
		}

	private def bindingsForObjectFetch(doFetch: DataObjectFetchNode, bindings: BindingSet): Iterator[BindingSet] = {
		val index = indexThunk()

		val setters: Seq[(QueryBindingSet, ObjInfo) => Unit] = doFetch.varNames.toSeq.map{case (prop, varName) =>

			def setter(accessor: ObjInfo => Value): (QueryBindingSet, ObjInfo) => Unit =
				(bs, oinfo) => bs.setBinding(varName, accessor(oinfo))
			def setterOpt(accessor: ObjInfo => Option[Value]): (QueryBindingSet, ObjInfo) => Unit =
				(bs, oinfo) => accessor(oinfo).foreach(bs.setBinding(varName, _))

			prop match{
				case DobjUri         => setter(_.uri)
				case Spec            => setter(_.spec)
				case Station         => setter(_.station)
				case Submitter       => setter(_.submitter)
				case FileName        => (_: QueryBindingSet, _: ObjInfo) => ()
				case FileSize        => setterOpt(_.sizeInBytes.map(index.factory.createLiteral))
				case SubmissionStart => setterOpt(_.submissionStartTime)
				case SubmissionEnd   => setterOpt(_.submissionEndTime)
				case DataStart       => setterOpt(_.dataStartTime)
				case DataEnd         => setterOpt(_.dataEndTime)
			}
		}

		val fetchRequest = getFetchRequest(doFetch, bindings)

		index.fetch(fetchRequest).map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			setters.foreach{_(bs, oinfo)}
			bs
		}
		// .zipWithIndex.collect{
		// 	case (bs, i) =>
		// 		if(i < 3 || i == 10 || i == 100 || i == 1000 || i == 10000) println(s"binding $i : $bs")
		// 		if(i == 3) println("...")
		// 		bs
		// }
	}

	private def getFetchRequest(doFetch: DataObjectFetchNode, bindings: BindingSet): DataObjectFetch = {
		val orig = doFetch.fetchRequest

		val betterReqOpt: Option[DataObjectFetch] = for(
			specVar <- doFetch.varNames.get(Spec) if bindings.hasBinding(specVar) &&
				orig.selections.exists(sel => sel.category == Spec && sel.values.isEmpty);
			spec <- bindings.getBinding(specVar).getValue match {
				case uri: IRI => Some(uri)
				case _ => None
			}
		) yield orig.withSelection(selection(Spec, Seq(spec)))

		betterReqOpt.getOrElse(orig)
	}
}
