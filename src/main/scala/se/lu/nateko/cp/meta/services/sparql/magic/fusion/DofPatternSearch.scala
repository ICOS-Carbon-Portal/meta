package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.jdk.CollectionConverters.IterableHasAsScala

import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

class DofPatternSearch(meta: CpmetaVocab){

	def find(e: TupleExpr): DofPattern = mergeProjectionPatterns(find0(e))

	private def find0(e: TupleExpr): DofPattern = e match{

		case sp: StatementPattern =>
			val pred = sp.getPredicateVar
			if(pred.isAnonymous && pred.hasValue) pred.getValue match{

				case iri: IRI =>
					val obj = sp.getObjectVar
					val subj = sp.getSubjectVar
					val subjVar = subj.getName

					val varVals = if(obj.hasValue) Map(
						subjVar -> ValueInfoPattern(
							Some(Set(obj.getValue)),
							Nil
						)
					) else Map.empty[String, ValueInfoPattern]

					DofPattern.Empty.copy(
						dobjVar = if(iri === meta.hasObjectSpec && !subj.isAnonymous) Some(subjVar) else None,
						propPaths = Map(subjVar -> Seq(StatementPattern2(iri, sp))),
						varValues = varVals
					)

				case _ => DofPattern.Empty
			} else DofPattern.Empty

		case bsa: BindingSetAssignment => bsa.getBindingNames.asScala.toList match{

			case varName :: Nil =>

				val values = bsa.getBindingSets().asScala.map(_.getValue(varName)).toSet
				val vif = ValueInfoPattern(Some(values), Seq(bsa))
				DofPattern.Empty.copy(varValues = Map(varName -> vif))

			case _ => DofPattern.Empty
		}

		case join: Join =>
			find0(join.getLeftArg).join(find0(join.getRightArg))

		case join: LeftJoin => new DofPatternList(
			Seq(find0(join.getLeftArg), find0(join.getRightArg)).flatMap{
				case inner: DofPatternList => inner.subs
				case other => Seq(other)
			}: _*
		)

		case union: Union => new DofPatternUnion(
			subs = Seq(find0(union.getLeftArg), find0(union.getRightArg)).flatMap{
				case inner: DofPatternUnion => inner.subs
				case other => Seq(other)
			},
			union
		)

		case filter: Filter =>
			DofPattern.Empty.copy(filters = Seq(filter.getCondition)).join(find0(filter.getArg))

		case slice: Slice => find0(slice.getArg) match{
			case pdofp: ProjectionDofPattern =>
				val newOffset = if (pdofp.offset.isEmpty) Some(new OffsetPattern(slice)) else None
				pdofp.copy(offset = newOffset)
			case other => other
		}

		case proj: Projection => find0(proj.getArg) match{
			case pdofp: ProjectionDofPattern => pdofp
			case any => ProjectionDofPattern(any, None, None, None)
		}

		case ext: Extension => find0(ext.getArg)

		case order: Order =>
			val inner = find0(order.getArg)

			val opOpt: Option[OrderPattern] = {
				val elems = order.getElements
				if(elems.size == 1){
					val elem = elems.get(0)
					Option(elem.getExpr).collect{
						case v: Var => OrderPattern(order, v.getName, !elem.isAscending)
					}
				} else None
			}
			opOpt.fold(inner)(op => inner match {
				case _: PlainDofPattern | _: DofPatternUnion | _: ProjectionDofPattern =>
					ProjectionDofPattern(inner, Some(op), None, None)
				case _ =>
					inner
			})

		case _ => DofPattern.Empty

	}

	def mergeProjectionPatterns(patt: DofPattern): DofPattern = patt match{

		case pdp @ ProjectionDofPattern(inner: ProjectionDofPattern, _, _, None) =>
			mergeProjectionPatterns(pdp.copy(inner = DofPattern.Empty) join inner)

		case pdp @ ProjectionDofPattern(_, _, _, Some(outer)) =>
			pdp.copy(outer = Some(mergeProjectionPatterns(outer)))

		case other => other

	}

}
