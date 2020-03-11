package se.lu.nateko.cp.meta.services.sparql.index

import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.FilterRequest
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch._

class DataObjectFetch(
	val selections: Seq[Selection],
	val filtering: Filtering,
	val sort: Option[SortBy],
	val offset: Int
){
	def withSelection(sel: Selection): DataObjectFetch = {
		val newSels = selections.filterNot(_.category == sel.category) :+ sel
		new DataObjectFetch(newSels, filtering, sort, offset)
	}
}

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

	def selection(cat: CategProp)(vals: Seq[cat.ValueType]) = new Selection{
		val category = cat
		//TODO Get rid of the following type cast (might not be needed in versions of Scala after 2.12)
		val values = vals.asInstanceOf[Seq[category.ValueType]]
	}

	def filter(prop: ContProp)(cond: FilterRequest[prop.ValueType]) = new Filter{
		val property = prop
		//TODO Get rid of the following type cast (might not be needed in versions of Scala after 2.12)
		val condition = cond.asInstanceOf[FilterRequest[property.ValueType]]
	}

	object Filter{
		def unapply(f: Filter) = Some(f.property -> f.condition)
	}

	object Selection{
		def unapply(s: Selection): Option[(CategProp, Seq[s.category.ValueType])] = Some(s.category -> s.values)
	}

	case class SortBy(property: ContProp, descending: Boolean)

	sealed trait Property{type ValueType}

	sealed trait UriProperty extends Property{type ValueType = IRI}
	sealed trait OptUriProperty extends CategProp{ type ValueType = Option[IRI]}

	final case object DobjUri extends UriProperty

	sealed trait ContProp extends Property

	sealed trait LongProperty extends ContProp{type ValueType = Long}
	sealed trait DateProperty extends LongProperty

	final case object FileName extends ContProp{type ValueType = String}
	final case object FileSize extends LongProperty
	final case object SamplingHeight extends ContProp{type ValueType = Float}
	final case object SubmissionStart extends DateProperty
	final case object SubmissionEnd extends DateProperty
	final case object DataStart extends DateProperty
	final case object DataEnd extends DateProperty

	sealed trait CategProp extends Property{type ValueType <: AnyRef}

	final case object Spec extends CategProp with UriProperty
	final case object Station extends OptUriProperty
	final case object Site extends OptUriProperty
	final case object Submitter extends CategProp with UriProperty
}
