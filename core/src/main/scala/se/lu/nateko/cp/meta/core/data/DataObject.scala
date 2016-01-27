package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

case class UriResource(uri: URI, label: Option[String])

case class DataObjectSpec(format: UriResource, encoding: UriResource, dataLevel: Int)

case class DataSubmission(submitter: UriResource, start: Instant, stop: Option[Instant])
case class DataProduction(
	producer: UriResource,
	start: Instant,
	stop: Instant,
	pos: Option[Map[String, Double]],
	coverage: Option[String]
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

sealed trait DataObjectStatus
case object NotComplete extends DataObjectStatus
case object UploadOk extends DataObjectStatus
