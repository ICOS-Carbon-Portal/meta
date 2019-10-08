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

	class Filtering(val filters: Seq[Filter], val filterDeprecated: Boolean, val requiredProps: Seq[ContProp[_]])

	sealed trait Filter{
		type ValueType
		def property: ContProp[ValueType]
		def condition: FilterRequest[ValueType]
	}

	sealed trait Selection{
		type ValueType <: AnyRef
		def category: CategProp[ValueType]
		def values: Seq[ValueType]
	}

	def selection[T <: AnyRef](cat: CategProp[T], vals: Seq[T]) = new Selection{
		type ValueType = T
		val category = cat
		val values = vals
	}

	def filter[T](prop: ContProp[T], cond: FilterRequest[T]) = new Filter{
		type ValueType = T
		val property = prop
		val condition = cond
	}

	case class SortBy(property: ContProp[_], descending: Boolean)

	sealed trait Property[T]

	sealed trait ContProp[T] extends Property[T]

	final case object FileName extends ContProp[String]
	final case object FileSize extends ContProp[Long]
	final case object SubmissionStart extends ContProp[Long]
	final case object SubmissionEnd extends ContProp[Long]
	final case object DataStart extends ContProp[Long]
	final case object DataEnd extends ContProp[Long]

	sealed trait CategProp[T] extends Property[T]

	final case object Spec extends CategProp[IRI]
	final case object Station extends CategProp[Option[IRI]]
	final case object Submitter extends CategProp[IRI]
}
