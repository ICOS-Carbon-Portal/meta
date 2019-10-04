package se.lu.nateko.cp.meta.services.sparql.index

import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.sparql.index.HierarchicalBitmap.FilterRequest
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch._

class DataObjectFetch(
	val specs: Seq[IRI],
	val stations: Seq[Option[IRI]],
	val submitters: Seq[IRI],
	val filtering: Filtering,
	val sort: Option[SortBy],
	val offset: Int
)

object DataObjectFetch{

	class Filtering(val filters: Seq[AnyFilter], val filterDeprecated: Boolean, val requiredProps: Seq[Property[_]])

	sealed trait AnyFilter{
		type ValueType
		def property: Property[ValueType]
		def condition: FilterRequest[ValueType]
	}

	case class Filter[T](property: Property[T], condition: FilterRequest[T]) extends AnyFilter{type ValueType = T}

	case class SortBy(property: Property[_], descending: Boolean)

	sealed trait Property[T]

	final case object FileName extends Property[String]
	final case object FileSize extends Property[Long]
	final case object SubmissionStart extends Property[Long]
	final case object SubmissionEnd extends Property[Long]
	final case object DataStart extends Property[Long]
	final case object DataEnd extends Property[Long]

}
