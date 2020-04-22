package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.query.algebra._

/**
  * Data object fetch pattern
  */
sealed trait DofPattern{
	final def join(other: DofPattern): DofPattern = if(this eq DofPattern.Empty) other else other match{
		case DofPattern.Empty => this
		case pdp: ProjectionDofPattern => this &: pdp
		case _ => joinInner(other)
	}
	protected def joinInner(other: DofPattern): DofPattern
}

sealed trait QVar{
	def name: String
}
case class NamedVar(name: String) extends QVar
case class AnonVar(name: String) extends QVar
object QVar{
	def apply(v: Var): QVar = if(v.isAnonymous) AnonVar(v.getName) else NamedVar(v.getName)
}

final case class PlainDofPattern(
	dobjVar: Option[NamedVar],
	propPaths: Map[QVar, Seq[StatementPattern2]],
	varValues: Map[QVar, ValueInfoPattern],
	filters: Seq[ValueExpr]
) extends DofPattern{

	protected def joinInner(another: DofPattern): DofPattern = another match{

		case other: PlainDofPattern => (dobjVar, other.dobjVar) match{

			case (Some(dobj1), Some(dobj2)) if(dobj1 != dobj2) =>
				new DofPatternList(this, another)

			case _ =>
				val newDobjVar = dobjVar.orElse(other.dobjVar)

				val newPropPaths = (Iterable.from(propPaths) ++ other.propPaths)
					.groupMapReduce(_._1)(_._2)(_ ++ _)

				val newVarValues = (Iterable.from(varValues) ++ other.varValues)
					.groupMapReduce(_._1)(_._2)(_ merge _)

				PlainDofPattern(
					newDobjVar,
					newPropPaths,
					newVarValues,
					filters ++ other.filters
				)
		}

		case j: DofPatternList => new DofPatternList(j.subs.map(this.join _): _*)

		case u: DofPatternUnion => new DofPatternUnion(u.subs.map(this.join _), u.union)

		case unexpected => throw new MatchError(unexpected)
	}

}

final case class ProjectionDofPattern(
	inner: DofPattern,
	orderBy: Option[OrderPattern],
	offset: Option[OffsetPattern],
	outer: Option[DofPattern]
) extends DofPattern {
	protected def joinInner(other: DofPattern): DofPattern = copy(
		outer = Some(outer.fold(other)(_ join other))
	)

	def &:(left: DofPattern): DofPattern = copy(
		outer = Some(outer.fold(left)(left.join))
	)
}

final class DofPatternList(val subs: DofPattern*) extends DofPattern{
	protected def joinInner(other: DofPattern): DofPattern = new DofPatternList(subs.map(_.join(other)) :_*)
}

final class DofPatternUnion(val subs: Seq[DofPattern], val union: Union) extends DofPattern{
	protected def joinInner(other: DofPattern): DofPattern = new DofPatternUnion(subs.map(_.join(other)), union)
}

object DofPattern{
	val Empty = PlainDofPattern(None, Map.empty, Map.empty, Nil)
}

final case class StatementPattern2(pred: IRI, sp: StatementPattern){
	assert(sp.getPredicateVar.getValue == pred, "StatementPattern's predicate value must be a specified IRI")
	def sourceVar = QVar(sp.getSubjectVar)
	def targetVar = QVar(sp.getObjectVar)
}

final case class ValueInfoPattern(vals: Option[Set[Value]], providers: Seq[TupleExpr]){
	def merge(other: ValueInfoPattern): ValueInfoPattern = {
		val newVals = (vals, other.vals) match{
			case (Some(vs1), Some(vs2)) => Some(vs1.intersect(vs2))
			case _ => vals.orElse(other.vals)
		}
		ValueInfoPattern(newVals, providers ++ other.providers)
	}
}

final case class OrderPattern(expr: Order, sortVar: NamedVar, descending: Boolean)

final class OffsetPattern(val slice: Slice){
	def offset = slice.getOffset.toInt
}