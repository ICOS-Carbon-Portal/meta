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
import scala.jdk.CollectionConverters.IteratorHasAsJava
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.sparql.index._
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.StatsFetchNode
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.FilterPatternSearch
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.EqualsFilter


class CpEvaluationStrategyFactory(
	fedResolver: FederatedServiceResolver,
	indexThunk: () => CpIndex
) extends AbstractEvaluationStrategyFactory{

	override def createEvaluationStrategy(dataSet: Dataset, tripleSrc: TripleSource) =
		new TupleFunctionEvaluationStrategy(tripleSrc, dataSet, fedResolver, new TupleFunctionRegistry){

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
		import statFetch.{group, countVarName}

		val allStatEntries = index.statEntries(group.filter)

		val statEntries: Iterable[StatEntry] = group.siteVar match{
			case Some(_) => allStatEntries
			case None =>
				allStatEntries.groupBy(se => se.key.copy(site = None)).map{
					case (key, subEntries) => StatEntry(key, subEntries.map(_.count).sum)
				}
		}
		statEntries.iterator.map{se =>
			val bs = new QueryBindingSet
			bs.setBinding(countVarName, index.factory.createLiteral(se.count))
			bs.setBinding(group.submitterVar, se.key.submitter)
			bs.setBinding(group.specVar, se.key.spec)
			for(station <- se.key.station) bs.setBinding(group.stationVar, station)
			for(siteVar <- group.siteVar; site <- se.key.site) bs.setBinding(siteVar, site)
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
				case Site            => setter(_.site)
				case Submitter       => setter(_.submitter)
				case FileName        => (_, _) => ()
				case VariableName    => (_, _) => ()
				case _: BoolProperty => (_, _) => ()
				case FileSize        => setterOpt(_.sizeInBytes.map(index.factory.createLiteral))
				case SamplingHeight  => setterOpt(_.samplingHeightMeters.map(index.factory.createLiteral))
				case SubmissionStart => setterOpt(_.submissionStartTime)
				case SubmissionEnd   => setterOpt(_.submissionEndTime)
				case DataStart       => setterOpt(_.dataStartTime)
				case DataEnd         => setterOpt(_.dataEndTime)
			}
		}

		val fetchRequest = new RequestInitializer(doFetch.varNames, bindings)
			.enrichWithFilters(doFetch.fetchRequest)

		index.fetch(fetchRequest).map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			setters.foreach{_(bs, oinfo)}
			bs
		}

	}

	def iteration(iter: Iterator[BindingSet]) =
		new CloseableIteratorIteration[BindingSet, QueryEvaluationException](iter.asJava)

}


class RequestInitializer(varNames: Map[Property, String], bindings: BindingSet){

	def enrichWithFilters(orig: DataObjectFetch): DataObjectFetch = {

		val extraFilters: Seq[Filter] = varNames.flatMap{ case (prop, varName) =>
			Option(bindings.getValue(varName)).flatMap(
				FilterPatternSearch.parsePropValueFilter(prop, _)
			)
		}.toIndexedSeq

		if(extraFilters.isEmpty) orig else orig.copy(
			filter = And(extraFilters :+ orig.filter).optimize
		)
	}
}
