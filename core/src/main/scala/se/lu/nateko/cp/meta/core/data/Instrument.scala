package se.lu.nateko.cp.meta.core.data
// import se.lu.nateko.cp.meta.api.UriId
import java.time.Instant
// import se.lu.nateko.cp.meta.icos.InstrumentDeployment

case class InstrumentDeployment(
	// cpId: IRI,
	// stationTcId: TcId[T],
	// stationUriId: UriId,
	pos: Option[Position],
	variable: Option[String],
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
