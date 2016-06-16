package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

import DataTheme.DataTheme
import spray.json.JsValue

object DataTheme extends Enumeration{
	val Atmosphere, Ecosystem, Ocean, CP, CAL, NonICOS = Value
	type DataTheme = Value
}

case class UriResource(uri: URI, label: Option[String])
case class Position(lat: Double, lon: Double)
case class TimeInterval(start: Instant, stop: Instant)

case class Station(
	uri: URI,
	label: String,
	theme: DataTheme,
	pos: Option[Position],
	coverage: Option[String]
)

case class DataObjectSpec(
	format: UriResource,
	encoding: UriResource,
	dataLevel: Int,
	datasetSpec: Option[JsValue]
)

case class DataAcquisition(station: Station, interval: Option[TimeInterval])
case class DataProduction(
	creator: UriResource,
	contributors: Seq[UriResource],
	hostOrganization: Option[UriResource],
	dateTime: Instant
)
case class DataSubmission(submitter: UriResource, start: Instant, stop: Option[Instant])

case class SpatialCoverage(min: Position, max: Position, label: Option[String]){
	def geoJson: String = ???
}

case class TemporalCoverage(interval: TimeInterval, resolution: Option[String])

case class L2OrLessSpecificMeta(
	aquisition: DataAcquisition,
	productionInfo: Option[DataProduction]
)
case class L3SpecificMeta(
	spatial: SpatialCoverage,
	temporal: TemporalCoverage,
	title: Option[String],
	description: Option[String],
	productionInfo: DataProduction,
	theme: DataTheme
)

case class DataObject(
	hash: Sha256Sum,
	accessUrl: Option[URI],
	pid: Option[String],
	fileName: Option[String],
	submission: DataSubmission,
	specification: DataObjectSpec,
	specificInfo: Either[L3SpecificMeta, L2OrLessSpecificMeta]
){
	def production: Option[DataProduction] = specificInfo.left.toOption.map(_.productionInfo)
	def pos: Option[Position] = specificInfo.right.toOption.flatMap(_.aquisition.station.pos)
	def coverage: Option[String] = specificInfo.fold(
		l3 => Some(l3.spatial.geoJson),
		l2 => l2.aquisition.station.coverage
	)
	def theme: DataTheme = specificInfo.fold(_.theme, _.aquisition.station.theme)
}
