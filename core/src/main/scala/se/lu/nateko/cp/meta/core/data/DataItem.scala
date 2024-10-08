package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


sealed trait PlainStaticItem extends DataItem:
	def hash: Sha256Sum
	def name: String
	def asUriResource = UriResource(res, Some(name), Nil)

// collection class should be declared first for the sake of Python parsing,
// because it needs to be tried first (otherwise all PlainStatiItems will parse as PlainStaticObjects)
// because in serialization 'name' property is added to collection members for backwards compatibility
final case class PlainStaticCollection(res: URI, hash: Sha256Sum, title: String) extends PlainStaticItem:
	def name: String = title

final case class PlainStaticObject(res: URI, hash: Sha256Sum, name: String) extends PlainStaticItem

sealed trait DataItemCollection extends DataItem:
	type M <: DataItem
	def members: Seq[M]
	def creator: Organization
	def title: String
	def description: Option[String]
	def doi: Option[String]

// DataItem declaration is placed here to produce correct Python type declarations
// (Python type checker is sensitive to the order)
sealed trait DataItem:
	def res: URI

final case class StaticCollection(
	res: URI,
	hash: Sha256Sum,
	members: Seq[PlainStaticItem],
	creator: Organization,
	title: String,
	description: Option[String],
	previousVersion: Option[URI],
	nextVersion: OptionalOneOrSeq[URI],
	latestVersion: OneOrSeq[URI],
	parentCollections: Seq[UriResource],
	doi: Option[String],
	coverage: Option[GeoFeature],
	documentation: Option[PlainStaticObject],
	references: References
) extends DataItemCollection with CitableItem:
	type M = PlainStaticItem


trait CitableItem:
	def doi: Option[String]
	def references: References
	def nextVersion: OptionalOneOrSeq[URI]
	def latestVersion: OneOrSeq[URI]
