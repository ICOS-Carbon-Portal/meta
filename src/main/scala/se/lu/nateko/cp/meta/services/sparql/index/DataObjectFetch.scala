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

	final class CategFilter(val category: CategProp, val values: Seq[category.ValueType]) extends Filter
	object CategFilter{
		def unapply(f: CategFilter) = (f.category, f.values)
	}

	final class GeneralCategFilter(val category: CategProp, val condition: category.ValueType => Boolean) extends Filter{
		override def toString = s"GeneralCategFilter($category)"
		def testUnsafe(v: AnyRef): Boolean = condition(v.asInstanceOf[category.ValueType])
	}

	final class ContFilter(val property: ContProp, val condition: FilterRequest[property.ValueType]) extends Filter
	object ContFilter{
		def unapply(f: ContFilter) = (f.property, f.condition)
		class FilterExtractor(val property: ContProp){
			def unapply(f: ContFilter): Option[FilterRequest[property.ValueType]] = f match{
				case ContFilter(`property`, filtReq) => Some(filtReq.asInstanceOf[FilterRequest[property.ValueType]])
				case _ => None
			}
		}
	}

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
