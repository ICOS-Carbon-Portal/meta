package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet
import org.eclipse.rdf4j.query.algebra.evaluation.QueryEvaluationStep
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver
import org.eclipse.rdf4j.query.algebra.evaluation.impl.*
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.EnvriResolver
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.*
import se.lu.nateko.cp.meta.services.sparql.magic.index.StatEntry
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.jdk.CollectionConverters.IteratorHasAsJava
import scala.jdk.CollectionConverters.IteratorHasAsScala


class CpEvaluationStrategyFactory(
	fedResolver: FederatedServiceResolver,
	index: CpIndex,
	enricher: StatementsEnricher,
	indexEnabled: Boolean
)(using envConf: EnvriConfigs) extends DefaultEvaluationStrategyFactory(fedResolver){
	import index.{vocab => metaVocab}
	private val logger = LoggerFactory.getLogger(this.getClass)

	override def createEvaluationStrategy(dataSet: Dataset, baseTripleSrc: TripleSource, stats: EvaluationStatistics) = {

		val envriOpt = Option(dataSet)
			.filter(_ => envConf.size > 1) // no need for ENVRI filtering if there is only one
			.flatMap(_.getDefaultGraphs.iterator.asScala.nextOption())
			.flatMap(iri => EnvriResolver.infer(iri.toJava))

		val tripleSrc = CpEnrichedTripleSource(baseTripleSrc, enricher)
		new DefaultEvaluationStrategy(tripleSrc, dataSet, fedResolver, 0, stats){strat =>

			setOptimizerPipeline(CpQueryOptimizerPipeline(strat, tripleSrc, stats))

			override def precompile(expr: TupleExpr, context: QueryEvaluationContext): QueryEvaluationStep = expr match

				case doFetch: DataObjectFetchNode if indexEnabled =>
					qEvalStep(bindingsForObjectFetch(doFetch, _))

				case statsFetch: StatsFetchNode if indexEnabled =>
					val statsBindings = bindingsForStatsFetch(statsFetch).toIndexedSeq
					qEvalStep(_ => statsBindings.iterator)

				case UniqueKeywordsNode(bindingName, doFetch) => {
					qEvalStep(existingBindings => {
						val fetchRequest = getFilterEnrichedDobjFetch(doFetch, existingBindings)
						val keywords = index.getUniqueKeywords(fetchRequest).toSeq.sorted
						val bs = new QueryBindingSet(existingBindings)
						bs.setBinding(bindingName, index.factory.createLiteral(keywords.mkString(",")))
						Iterator.single(bs)
					})
				}

				case _ => super.precompile(expr, context)

			override def optimize(expr: TupleExpr, stats: EvaluationStatistics, bindings: BindingSet): TupleExpr = {
				logger.debug("Original query model:\n{}", expr)

				val queryExpr: TupleExpr = TupleExprCloner.cloneExpr(expr)

				if indexEnabled then
					val dofps = new DofPatternSearch(metaVocab)
					val fuser = new DofPatternFusion(metaVocab, envriOpt)

					val pattern = dofps.find(queryExpr)
					val fusions = fuser.findFusions(pattern)
					DofPatternRewrite.rewrite(queryExpr, fusions)

					logger.debug("Fused query model:\n{}", queryExpr)

				val finalExpr = super.optimize(queryExpr, stats, bindings)

				logger.debug("Fully optimized final query model:\n{}", finalExpr)
				finalExpr
			}
		}
	}

	private def bindingsForStatsFetch(statFetch: StatsFetchNode): Iterator[BindingSet] = {
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
			bs.setBinding(countVarName, index.factory.createLiteral(se.count.toString, XSD.INTEGER))
			bs.setBinding(group.submitterVar, se.key.submitter)
			bs.setBinding(group.specVar, se.key.spec)
			for(station <- se.key.station) bs.setBinding(group.stationVar, station)
			for(siteVar <- group.siteVar; site <- se.key.site) bs.setBinding(siteVar, site)
			bs
		}
	}

	private def bindingsForObjectFetch(doFetch: DataObjectFetchNode, bindings: BindingSet): Iterator[BindingSet] = {
		val f = index.factory
		type BindingSetter = (QueryBindingSet, ObjInfo) => Unit
		val noopSetter: BindingSetter = (_, _) => ()

		val setters: Seq[BindingSetter] = doFetch.varNames.toSeq.map{case (prop, varName) =>

			def setter(accessor: ObjInfo => Value): BindingSetter =
				(bs, oinfo) => bs.setBinding(varName, accessor(oinfo))
			def setterOpt(accessor: ObjInfo => Option[Value]): BindingSetter =
				(bs, oinfo) => accessor(oinfo).foreach(bs.setBinding(varName, _))

			prop match{
				case DobjUri         => setter(_.uri(f))
				case Spec            => setter(_.spec)
				case Station         => setter(_.station)
				case Site            => setter(_.site)
				case Submitter       => setter(_.submitter)
				case FileName        => setterOpt(_.fileName.map(f.createLiteral))
				case _: BoolProperty => noopSetter
				case _: StringCategProp => noopSetter
				case EnvriProp       => noopSetter
				case FileSize        => setterOpt(_.sizeInBytes.map(f.createLiteral))
				case SamplingHeight  => setterOpt(_.samplingHeightMeters.map(f.createLiteral))
				case SubmissionStart => setterOpt(_.submissionStartTime.map(f.createDateTimeLiteral))
				case SubmissionEnd   => setterOpt(_.submissionEndTime.map(f.createDateTimeLiteral))
				case DataStart       => setterOpt(_.dataStartTime.map(f.createDateTimeLiteral))
				case DataEnd         => setterOpt(_.dataEndTime.map(f.createDateTimeLiteral))
				case _: GeoProp      => noopSetter
			}
		}

		val fetchRequest = getFilterEnrichedDobjFetch(doFetch, bindings)

		index.fetch(fetchRequest).map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			setters.foreach{_(bs, oinfo)}
			bs
		}

	}

	def qEvalStep(eval: BindingSet => Iterator[BindingSet]) = new QueryEvaluationStep{
		override def evaluate(bindings: BindingSet) =
			new CloseableIteratorIteration[BindingSet](eval(bindings).asJava)
	}

}


private def getFilterEnrichedDobjFetch(dofNode: DataObjectFetchNode, bindings: BindingSet): DataObjectFetch = {

	val extraFilters: Seq[Filter] = dofNode.varNames.flatMap{ case (prop, varName) =>
		Option(bindings.getValue(varName)).flatMap(
			FilterPatternSearch.parsePropValueFilter(prop, _)
		)
	}.toIndexedSeq

	val orig = dofNode.fetchRequest

	if(extraFilters.isEmpty) orig else orig.copy(
		filter = And(extraFilters :+ orig.filter).optimize
	)
}
