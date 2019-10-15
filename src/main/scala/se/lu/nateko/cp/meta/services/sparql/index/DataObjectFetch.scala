package se.lu.nateko.cp.meta.services.sparql.index

import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.FilterRequest
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch._

class DataObjectFetch(
	val selections: Seq[Selection],
	val filtering: Filtering,
	val sort: Option[SortBy],
	val offset: Int
)

object DataObjectFetch{

	class Filtering(val filters: Seq[Filter], val filterDeprecated: Boolean, val requiredProps: Seq[ContProp])

	sealed trait Filter{
		val property: ContProp
		def condition: FilterRequest[property.ValueType]
	}

	sealed trait Selection{
		val category: CategProp
		def values: Seq[category.ValueType]
	}

	def selection[T <: AnyRef](cat: CategProp{type ValueType = T}, vals: Seq[T]) = new Selection{
		val category = cat
		val values = vals
	}

	def filter[T](prop: ContProp{type ValueType = T}, cond: FilterRequest[T]) = new Filter{
		val property = prop
		val condition = cond
	}

	object Filter{
		def unapply(f: Filter) = Some(f.property -> f.condition)
	}

	case class SortBy(property: ContProp, descending: Boolean)

	sealed trait Property{type ValueType}

	sealed trait UriProperty extends Property{type ValueType = IRI}

	final case object DobjUri extends UriProperty

	sealed trait ContProp extends Property

	sealed trait LongProperty extends ContProp{type ValueType = Long}

	final case object FileName extends ContProp{type ValueType = String}
	final case object FileSize extends LongProperty
	final case object SubmissionStart extends LongProperty
	final case object SubmissionEnd extends LongProperty
	final case object DataStart extends LongProperty
	final case object DataEnd extends LongProperty

	sealed trait CategProp extends Property{type ValueType <: AnyRef}

	final case object Spec extends CategProp with UriProperty
	final case object Station extends CategProp{ type ValueType = Option[IRI]}
	final case object Submitter extends CategProp with UriProperty
}
