package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.model.IRI
import org.roaringbitmap.buffer.{BufferFastAggregation, ImmutableRoaringBitmap, MutableRoaringBitmap}
import se.lu.nateko.cp.meta.services.sparql.index.*
import se.lu.nateko.cp.meta.services.sparql.magic.index.{IndexData, emptyBitmap}
import se.lu.nateko.cp.meta.services.{CpVocab, MetadataException}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Filtering(data: IndexData, geo: Future[GeoIndex]) {
	import data.{objs, idLookup}

	def apply(filter: Filter): Option[ImmutableRoaringBitmap] = filter match {
		case And(filters) =>
			val geoFilts = filters.collect { case gf: GeoFilter => gf }

			if geoFilts.isEmpty then andFiltering(filters)
			else
				val nonGeoFilts = filters.filter:
					case gf: GeoFilter => false
					case _ => true
				val nonGeoBm = andFiltering(nonGeoFilts)
				val geoBms = geoFilts.flatMap(geoFiltering(_, nonGeoBm))
				if geoBms.isEmpty then nonGeoBm else and(geoBms ++ nonGeoBm)

		case Not(filter) => this.apply(filter) match {
				case None => Some(emptyBitmap)
				case Some(bm) => Some(negate(bm))
			}

		case Exists(prop) => prop match {
				case cp: ContProp => Some(data.bitmap(cp).all)
				case cp: CategProp => cp match {
						case optUriProp: OptUriProperty => data.categMap(optUriProp).get(None) match {
								case None => None
								case Some(deprived) if deprived.isEmpty => None
								case Some(deprived) => Some(negate(deprived))
							}
						case _ => None
					}
				case boo: BoolProperty => Some(data.boolBitmap(boo))
				case _: GeoProp => None
			}

		case ContFilter(property, condition) =>
			Some(data.bitmap(property).filter(condition))

		case CategFilter(category, values) if category == DobjUri =>
			val objIndices: Seq[Int] = values
				.collect { case iri: IRI => iri }
				.collect { case CpVocab.DataObject(hash, _) => idLookup.get(hash) }
				.flatten
			Some(ImmutableRoaringBitmap.bitmapOf(objIndices*))

		case CategFilter(category, values) if category == Keyword =>
			val keywords: Seq[String] = values.collect { case kw: String => kw }
			Some(data.getKeywordsBitmap(keywords))

		case CategFilter(category, values) =>
			val perValue = data.categMap(category)
			or(values.map(v => perValue.getOrElse(v, emptyBitmap)))

		case GeneralCategFilter(category, condition) => or(
				data.categMap(category).collect {
					case (cat, bm) if condition(cat) => bm
				}.toSeq
			)

		case gf: GeoFilter =>
			geoFiltering(gf, None)

		case Or(filters) =>
			collectUnless(filters.iterator.map(this.apply))(_.isEmpty).flatMap { bmOpts =>
				or(bmOpts.flatten)
			}

		case All =>
			None

		case Nothing =>
			Some(emptyBitmap)
	}

	private def andFiltering(filters: Seq[Filter]): Option[ImmutableRoaringBitmap] =
		collectUnless(filters.iterator.flatMap(this.apply))(_.isEmpty) match
			case None => Some(emptyBitmap)
			case Some(bms) => and(bms)

	private def geoFiltering(
		filter: GeoFilter,
		andFilter: Option[ImmutableRoaringBitmap]
	): Option[ImmutableRoaringBitmap] =
		geo.value match
			case None =>
				throw MetadataException("Geo index is not ready, please try again in a few minutes")
			case Some(Success(geoIndex)) => filter.property match
					case GeoIntersects => Some(geoIndex.getFilter(filter.geo, andFilter))
			case Some(Failure(exc)) =>
				throw Exception("Geo indexing failed", exc)

	private def negate(bm: ImmutableRoaringBitmap) =
		if objs.length == 0 then emptyBitmap else ImmutableRoaringBitmap.flip(bm, 0, objs.length.toLong)

	private def collectUnless[T](iter: Iterator[T])(cond: T => Boolean): Option[Seq[T]] = {
		var condHappened = false
		val seq = iter.takeWhile(elem => {
			condHappened = cond(elem)
			!condHappened
		}).toIndexedSeq
		if (condHappened) None else Some(seq)
	}

	private def or(bms: Seq[ImmutableRoaringBitmap]): Option[MutableRoaringBitmap] =
		if (bms.isEmpty) Some(emptyBitmap) else Some(BufferFastAggregation.or(bms*))

	private def and(bms: Seq[ImmutableRoaringBitmap]): Option[MutableRoaringBitmap] =
		if (bms.isEmpty) None else Some(BufferFastAggregation.and(bms*))
}
