package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.model.IRI

object DofPatternSearch{
	def find(e: TupleExpr): DofPattern = e match{

		case sp: StatementPattern =>
			val pred = sp.getPredicateVar
			if(pred.isAnonymous && pred.hasValue) pred.getValue match{

				case iri: IRI =>
					val obj = sp.getObjectVar
					val subj = sp.getSubjectVar.getName

					val varVals = if(obj.hasValue) Map(
						subj -> ValueInfoPattern(
							Some(Set(obj.getValue)),
							Nil
						)
					) else Map.empty[String, ValueInfoPattern]

					DofPattern.Empty.copy(
						propPaths = Map(subj -> Seq(StatementPattern2(iri, sp))),
						varValues = varVals
					)

				case _ => DofPattern.Empty
			} else DofPattern.Empty

		case join: Join =>
			find(join.getLeftArg).join(find(join.getRightArg))

		case join: LeftJoin =>
			new DofPatternList(find(join.getLeftArg), find(join.getRightArg))

		case slice: Slice =>
			find(slice.getArg) match{
				case pdofp: ProjectionDofPattern =>
					val newOffset = if (pdofp.offset.isEmpty) Some(new OffsetPattern(slice)) else None
					pdofp.copy(offset = newOffset)
				case other => other
			}

	}
}