package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


sealed trait StaticDataItem extends DataItem:
	def res: URI
	def hash: Sha256Sum

final case class PlainStaticObject(res: URI, hash: Sha256Sum, name: String) extends StaticDataItem{
	def asUriResource = UriResource(res, Some(name), Nil)
}

sealed trait DataItemCollection extends DataItem {
	type M <: DataItem
	def members: Seq[M]
	def creator: Organization
	def title: String
	def description: Option[String]
	def doi: Option[String]
}

sealed trait DataItem

final case class StaticCollection(
	res: URI,
	hash: Sha256Sum,
	members: Seq[StaticDataItem],
	creator: Organization,
	title: String,
	description: Option[String],
	previousVersion: Option[URI],
	nextVersion: OptionalOneOrSeq[URI],
	latestVersion: OneOrSeq[URI],
	doi: Option[String],
	coverage: Option[GeoFeature],
	documentation: Option[PlainStaticObject],
	references: References
) extends DataItemCollection with StaticDataItem with CitableItem:
	type M = StaticDataItem

trait CitableItem{
	def hash: Sha256Sum
	def doi: Option[String]
	def references: References
	def nextVersion: OptionalOneOrSeq[URI]
	def latestVersion: OneOrSeq[URI]
}
