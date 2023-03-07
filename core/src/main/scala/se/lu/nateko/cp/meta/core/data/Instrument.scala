package se.lu.nateko.cp.meta.core.data
import java.time.Instant
import java.net.URI

case class InstrumentDeployment(
	instrument: UriResource,
	station: Organization,
	pos: Option[Position],
	variableName: Option[String],
	forProperty: Option[UriResource],
	start: Option[Instant],
	stop: Option[Instant]
)

case class Instrument(
	self: UriResource,
	model: String,
	serialNumber: String,
	name: Option[String],
	vendor: Option[Organization],
	owner: Option[Organization],
	parts: Seq[UriResource],
	partOf: Option[UriResource],
	deployments: Seq[InstrumentDeployment]
)
