package se.lu.nateko.cp.meta.services.sparql

import scala.language.implicitConversions
import org.eclipse.rdf4j.model.IRI

package object index{

	import HierarchicalBitmap.FilterRequest

	case class DataObjectFetch(filter: Filter, sort: Option[SortBy], offset: Int)

	sealed trait Filter

	final case class And(filters: Seq[Filter]) extends Filter
	final case class Or(filters: Seq[Filter]) extends Filter
	final case class Not(filter: Filter) extends Filter
	object All extends Filter
	object Nothing extends Filter
	final case class Exists(prop: Property) extends Filter
	final case class CategFilter[T <: AnyRef](category: TypedCategProp[T], values: Seq[T]) extends Filter
	final case class GeneralCategFilter[T <: AnyRef](category: TypedCategProp[T], condition: T => Boolean) extends Filter{
		override def toString = s"GeneralCategFilter($category)"
	}
	final case class ContFilter[T](property: ContProp{type ValueType = T}, condition: FilterRequest[T]) extends Filter

	case class SortBy(property: ContProp, descending: Boolean)

	sealed trait Property{type ValueType}

	sealed trait BoolProperty extends Property{type ValueType = Boolean}
	object DeprecationFlag extends BoolProperty
	object HasVarList extends BoolProperty

	type TypedCategProp[T <: AnyRef] = CategProp{type ValueType = T}

	sealed trait ContProp extends Property

	sealed trait LongProperty extends ContProp{type ValueType = Long}
	sealed trait DateProperty extends LongProperty

	case object FileName extends ContProp{type ValueType = String}
	case object FileSize extends LongProperty
	case object SamplingHeight extends ContProp{type ValueType = Float}
	case object SubmissionStart extends DateProperty
	case object SubmissionEnd extends DateProperty
	case object DataStart extends DateProperty
	case object DataEnd extends DateProperty

	sealed trait CategProp extends Property{type ValueType <: AnyRef}

	sealed trait StringCategProp extends CategProp{type ValueType = String}
	sealed trait UriProperty extends CategProp{type ValueType = IRI}
	sealed trait OptUriProperty extends CategProp{ type ValueType = Option[IRI]}

	case object DobjUri extends UriProperty
	case object Spec extends UriProperty
	case object Station extends OptUriProperty
	case object Site extends OptUriProperty
	case object Submitter extends UriProperty
	case object VariableName extends StringCategProp
	case object Keyword extends StringCategProp
}
