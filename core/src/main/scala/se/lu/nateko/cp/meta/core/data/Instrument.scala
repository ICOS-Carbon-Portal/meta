package se.lu.nateko.cp.meta.core.data

case class Instrument(
	self: UriResource,
	model: String,
	serialNumber: String,
	name: Option[String],
	vendor: Option[Organization],
	owner: Option[Organization],
	parts: Seq[UriResource],
	partOf: Option[UriResource]
)
