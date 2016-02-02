package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

case class UriResource(uri: URI, label: Option[String])
case class DataProducer(
   uri: URI,
   label: String,
   theme: ProducerTheme,
   pos: Option[Position],
   coverage: Option[String]
)

case class Position(lat: Double, lon: Double)

case class DataObjectSpec(format: UriResource, encoding: UriResource, dataLevel: Int)

case class DataSubmission(submitter: UriResource, start: Instant, stop: Option[Instant])
case class DataProduction(
	producer: DataProducer,
	start: Instant,
	stop: Instant
)

case class DataObject(
	status: DataObjectStatus,
	hash: Sha256Sum,
	accessUrl: URI,
	pid: Option[String],
	fileName: Option[String],
	production: DataProduction,
	submission: DataSubmission,
	specification: DataObjectSpec
)


sealed trait ProducerTheme
case object ThemeAS extends ProducerTheme
case object ThemeES extends ProducerTheme
case object ThemeOS extends ProducerTheme


sealed trait DataObjectStatus
case object NotComplete extends DataObjectStatus
case object UploadOk extends DataObjectStatus
