package se.lu.nateko.cp.meta.services.sparql.index

import org.eclipse.rdf4j.model.IRI
import org.locationtech.jts.geom.Geometry
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.FilterRequest

import scala.language.implicitConversions

case class DataObjectFetch(filter: Filter, sort: Option[SortBy], offset: Int)

sealed trait Filter

final case class And(filters: Seq[Filter]) extends Filter
final case class Or(filters: Seq[Filter]) extends Filter
final case class Not(filter: Filter) extends Filter
object All extends Filter
object Nothing extends Filter
final case class Exists(prop: Property) extends Filter
final case class CategFilter[T <: AnyRef](category: CategProp{type ValueType = T}, values: Seq[T]) extends Filter
final case class GeneralCategFilter[T](category: CategProp{type ValueType = T}, condition: T => Boolean) extends Filter{
	override def toString = s"GeneralCategFilter($category)"
	def testUnsafe(v: AnyRef): Boolean = condition(v.asInstanceOf[category.ValueType])
}

final case class ContFilter[T](property: ContProp{type ValueType = T}, condition: FilterRequest[T]) extends Filter
object ContFilter{
	class FilterExtractor(val property: ContProp){
		def unapply(f: ContFilter[?]): Option[FilterRequest[property.ValueType]] = f match{
			case ContFilter(`property`, filtReq) => Some(filtReq.asInstanceOf[FilterRequest[property.ValueType]])
			case ContFilter(_, _) => None
		}
	}
}

final case class GeoFilter(property: GeoProp, geo: Geometry) extends Filter

case class SortBy(property: ContProp, descending: Boolean)

sealed trait Property extends java.io.Serializable{type ValueType}

sealed trait BoolProperty extends Property{type ValueType = Boolean}
case object DeprecationFlag extends BoolProperty
case object HasVarList extends BoolProperty

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

sealed trait GeoProp extends Property{type ValueType = Geometry}
case object GeoIntersects extends GeoProp

sealed trait CategProp extends Property{type ValueType <: AnyRef}

case object EnvriProp extends CategProp{ type ValueType = eu.icoscp.envri.Envri}
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

object Property{
	import scala.deriving.Mirror.SumOf
	import scala.compiletime.{erasedValue, summonInline}
	type ConcreteProp = Property & Singleton
	/** TODO ATTENTION Fragile code. findSubSingletons should be re-written to support automatic discovery of all singletons
	 * inheriting from Property. Then the explicit listing will not be needed.*/
	val allConcrete: Set[ConcreteProp] = {
		val specials: Iterable[ConcreteProp] = Iterable(FileName, FileSize, SamplingHeight, EnvriProp)
		Iterable(
			findSubSingletons[StringCategProp],
			findSubSingletons[UriProperty],
			findSubSingletons[OptUriProperty],
			findSubSingletons[BoolProperty],
			findSubSingletons[DateProperty],
			specials
		).flatten.toSet
	}

	private inline def findSubSingletons[T](using m: SumOf[T]): List[T & Singleton] =
		getSingles[m.MirroredElemTypes, m.MirroredType]

	private inline def getSingles[MET <: Tuple, T]: List[T & Singleton] = {
		inline erasedValue[MET] match{
			case _: EmptyTuple => Nil
			case _: (t *: ts) => summonInline[ValueOf[t]].value.asInstanceOf[T & Singleton] :: getSingles[ts, T]
		}
	}
}
