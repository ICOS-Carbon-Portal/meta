package se.lu.nateko.cp.meta.services.sparql.index

import org.eclipse.rdf4j.model.IRI
import org.locationtech.jts.geom.Geometry
import se.lu.nateko.cp.meta.core.algo.HierarchicalBitmap.FilterRequest

import scala.language.implicitConversions

case class DataObjectFetch[T](filter: Filter, sort: Option[SortBy[T]], offset: Int)

sealed trait Filter

final case class And(filters: Seq[Filter]) extends Filter
final case class Or(filters: Seq[Filter]) extends Filter
final case class Not(filter: Filter) extends Filter
object All extends Filter
object Nothing extends Filter
final case class Exists[T](prop: Property[T]) extends Filter
final case class CategFilter[T <: AnyRef](category: CategProp[T], values: Seq[T]) extends Filter
final case class GeneralCategFilter[T](category: CategProp[T], condition: T => Boolean) extends Filter {
	override def toString = s"GeneralCategFilter($category)"
	def testUnsafe(v: AnyRef): Boolean = condition(v.asInstanceOf[T])
}

final case class ContFilter[T](property: ContProp[T], condition: FilterRequest[T]) extends Filter
object ContFilter {
	class FilterExtractor[T](val property: ContProp[T]) {
		def unapply(f: ContFilter[?]): Option[FilterRequest[T]] = f match {
			case ContFilter(`property`, filtReq) => Some(filtReq.asInstanceOf[FilterRequest[T]])
			case ContFilter(_, _) => None
		}
	}
}

final case class GeoFilter[T](property: GeoProp[T], geo: Geometry) extends Filter

case class SortBy[T](property: ContProp[T], descending: Boolean)

sealed trait Property[T] extends java.io.Serializable

sealed trait BoolProperty extends Property[Boolean]
case object DeprecationFlag extends BoolProperty
case object HasVarList extends BoolProperty

sealed trait ContProp[T] extends Property[T]

sealed trait LongProperty extends ContProp[Long]
sealed trait DateProperty extends LongProperty

case object FileName extends ContProp [String]
case object FileSize extends LongProperty
case object SamplingHeight extends ContProp[Float]
case object SubmissionStart extends DateProperty
case object SubmissionEnd extends DateProperty
case object DataStart extends DateProperty
case object DataEnd extends DateProperty

sealed trait GeoProp extends Property[Geometry]
case object GeoIntersects extends GeoProp

sealed trait CategProp[T] extends Property[T]

sealed trait StringCategProp extends CategProp[String]
sealed trait UriProperty extends CategProp[IRI]
sealed trait OptUriProperty extends CategProp[Option[IRI]]

case object DobjUri extends UriProperty
case object Spec extends UriProperty
case object Station extends OptUriProperty
case object Site extends OptUriProperty
case object Submitter extends UriProperty
case object VariableName extends StringCategProp
case object Keyword extends StringCategProp

object Property {
	import scala.deriving.Mirror.SumOf
	import scala.compiletime.{erasedValue, summonInline}
	// type ConcreteProp = Property & Singleton
	/** TODO ATTENTION Fragile code. findSubSingletons should be re-written to support automatic discovery of all singletons
	 * inheriting from Property. Then the explicit listing will not be needed.*/
	val allConcrete = {
		val specials = Iterable(FileName, FileSize, SamplingHeight)
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
		inline erasedValue[MET] match {
			case _: EmptyTuple => Nil
			case _: (t *: ts) => summonInline[ValueOf[t]].value.asInstanceOf[T & Singleton] :: getSingles[ts, T]
		}
	}
}
/*

sealed trait Prop[T] extends java.io.Serializable
sealed trait Cont[T] extends Prop[T]
sealed trait StringProp extends Prop[String]
case object KeywordProp extends StringProp

case class StringContValues(values: Iterable[String], prop: StringProp)
 */
