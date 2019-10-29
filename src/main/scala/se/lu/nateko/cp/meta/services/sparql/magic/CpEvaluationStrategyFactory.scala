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
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchNode


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
						iteration(bindingsForObjectFetch(doFetch, bindings))

					case statsFetch: StatsFetchNode =>
						iteration(bindingsForStatsFetch(statsFetch))

					case _ =>
						super.evaluate(expr, bindings)
				}
			}
		}

	private def bindingsForStatsFetch(statFetch: StatsFetchNode): Iterator[BindingSet] = {
		val index = indexThunk()

		index.statEntries(statFetch.group.filtering).iterator.map{se =>
			val bs = new QueryBindingSet
			bs.setBinding(statFetch.countVarName, index.factory.createLiteral(se.count))
			bs.setBinding(statFetch.group.submitterVar, se.key.submitter)
			bs.setBinding(statFetch.group.specVar, se.key.spec)
			se.key.station.foreach{station =>
				bs.setBinding(statFetch.group.stationVar, station)
			}
			bs
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

		val fetchRequest = new RequestInitializer(doFetch.varNames, bindings)
			.initializeRequest(doFetch.fetchRequest)

		index.fetch(fetchRequest).map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			setters.foreach{_(bs, oinfo)}
			bs
		}

	}

	def iteration(iter: Iterator[BindingSet]) =
		new CloseableIteratorIteration[BindingSet, QueryEvaluationException](asJavaIterator(iter))

}

class RequestInitializer(varNames: Map[Property, String], bindings: BindingSet){

	def initializeRequest(orig: DataObjectFetch): DataObjectFetch = initStation(initSpec(orig))

	private def initSpec(req: DataObjectFetch): DataObjectFetch = initReq(req, Spec)(identity)
	private def initStation(req: DataObjectFetch): DataObjectFetch = initReq(req, Station)(Some(_))

	private def initReq(orig: DataObjectFetch, prop: CategProp)(mapper: IRI => prop.ValueType): DataObjectFetch = {

		val betterReqOpt: Option[DataObjectFetch] = for(

			propVar <- varNames.get(prop)
				if bindings.hasBinding(propVar) &&
					orig.selections.exists(sel => sel.category == prop && sel.values.isEmpty);

			propVal <- bindings.getBinding(propVar).getValue match {
				case uri: IRI => Some(mapper(uri))
				case _ => None
			}
		) yield orig.withSelection(selection(prop)(Seq(propVal)))

		betterReqOpt.getOrElse(orig)
	}
}
