package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{IRI, Value}
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver
import org.eclipse.rdf4j.query.algebra.evaluation.impl.*
import org.eclipse.rdf4j.query.algebra.evaluation.{QueryBindingSet, QueryEvaluationStep, TripleSource}
import org.eclipse.rdf4j.query.{BindingSet, Dataset}
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.services.sparql.TupleExprCloner
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.fusion.*
import se.lu.nateko.cp.meta.services.sparql.magic.index.StatEntry
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.jdk.CollectionConverters.IteratorHasAsJava
import org.eclipse.rdf4j.query.algebra.StatementPattern
// import org.eclipse.rdf4j.query.algebra.evaluation.impl.evaluationsteps.StatementPatternQueryEvaluationStep

class CpEvaluationStrategyFactory(
	fedResolver: FederatedServiceResolver,
	index: CpIndex,
	enricher: StatementsEnricher,
	indexEnabled: Boolean
) extends DefaultEvaluationStrategyFactory(fedResolver){
	import index.{vocab => metaVocab}
	private val logger = LoggerFactory.getLogger(this.getClass)

	override def createEvaluationStrategy(dataSet: Dataset, baseTripleSrc: TripleSource, stats: EvaluationStatistics) = {
		val tripleSrc = CpEnrichedTripleSource(baseTripleSrc, enricher)
		new DefaultEvaluationStrategy(tripleSrc, dataSet, fedResolver, 0, stats){strat =>

			setOptimizerPipeline(CpQueryOptimizerPipeline(strat, tripleSrc, stats))

			override def precompile(expr: TupleExpr, context: QueryEvaluationContext): QueryEvaluationStep = expr match

				case doFetch: DataObjectFetchNode if indexEnabled =>
					logger.info("Data object fetch!")
					qEvalStep(bindingsForObjectFetch(doFetch, _))

				case statsFetch: StatsFetchNode if indexEnabled =>
					val statsBindings = bindingsForStatsFetch(statsFetch).toIndexedSeq
					qEvalStep(_ => statsBindings.iterator)

				// TODO: Add KeywordFetchNode
				case statement: StatementPattern if indexEnabled =>
					val subject = statement.getSubjectVar()
					val predicate = statement.getPredicateVar().getValue()
					val obj = statement.getObjectVar()

					logger.info(s"statement: $statement")
					if (predicate == metaVocab.hasKeywords 
						&& subject.hasValue() // TODO: Extend StatementPatternQueryEvaluationStep in order to handle variable (multiple) subjects.
						&& subject.getValue().isIRI()
						&& !obj.hasValue()){
						qEvalStep(keywordBindings(subject.getValue().asInstanceOf[IRI], statement, _))

						// StatementPatternQueryEvaluationStep(statement, context, tripleSrc)
					}else{
						super.precompile(expr, context)
					}

				case thing =>
					super.precompile(expr, context)

			override def optimize(expr: TupleExpr, stats: EvaluationStatistics, bindings: BindingSet): TupleExpr = {
				logger.info("Original query model:\n{}", expr)

				val queryExpr: TupleExpr = TupleExprCloner.cloneExpr(expr)

				if indexEnabled then
					val dofps = new DofPatternSearch(metaVocab)
					val fuser = new DofPatternFusion(metaVocab)

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

	private def keywordBindings(subject: IRI, pattern: StatementPattern, bindings: BindingSet): Iterator[BindingSet] = {
		logger.info(s"names: ${bindings.getBindingNames()}, size: ${bindings.size()}, val: ${bindings.getValue("obj")}, subj: ${subject}")
		val bind = new QueryBindingSet(bindings)
		logger.info(s"thing: ${pattern.getObjectVar().getName()}")
		val keywords : List[String] = index.objectKeywords(subject)
		bind.setBinding(pattern.getObjectVar().getName(), index.factory.createLiteral("test"))
		List(bind).iterator
	}

	private def bindingsForObjectFetch(doFetch: DataObjectFetchNode, bindings: BindingSet): Iterator[BindingSet] = {

		val setters: Seq[(QueryBindingSet, ObjInfo) => Unit] = doFetch.varNames.toSeq.map{case (prop, varName) =>

			def setter(accessor: ObjInfo => Value): (QueryBindingSet, ObjInfo) => Unit =
				(bs, oinfo) => bs.setBinding(varName, accessor(oinfo))
			def setterOpt(accessor: ObjInfo => Option[Value]): (QueryBindingSet, ObjInfo) => Unit =
				(bs, oinfo) => accessor(oinfo).foreach(bs.setBinding(varName, _))

			val f = index.factory
			prop match{
				case DobjUri         => setter(_.uri(f))
				case Spec            => setter(_.spec)
				case Station         => setter(_.station)
				case Site            => setter(_.site)
				case Submitter       => setter(_.submitter)
				case FileName        => setterOpt(_.fileName.map(f.createLiteral))
				case _: BoolProperty => (_, _) => ()
				case _: StringCategProp => (_, _) => ()
				case FileSize        => setterOpt(_.sizeInBytes.map(f.createLiteral))
				case SamplingHeight  => setterOpt(_.samplingHeightMeters.map(f.createLiteral))
				case SubmissionStart => setterOpt(_.submissionStartTime.map(f.createDateTimeLiteral))
				case SubmissionEnd   => setterOpt(_.submissionEndTime.map(f.createDateTimeLiteral))
				case DataStart       => setterOpt(_.dataStartTime.map(f.createDateTimeLiteral))
				case DataEnd         => setterOpt(_.dataEndTime.map(f.createDateTimeLiteral))
				case _: GeoProp      => (_, _) => ()
			}
		}

		val fetchRequest = getFilterEnrichedDobjFetch(doFetch, bindings)

		index.fetch(fetchRequest).map{oinfo =>
			val bs = new QueryBindingSet(bindings)
			setters.foreach{_(bs, oinfo)}
			bs
		}

	}

	private def qEvalStep(eval: BindingSet => Iterator[BindingSet]) = new QueryEvaluationStep{
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
