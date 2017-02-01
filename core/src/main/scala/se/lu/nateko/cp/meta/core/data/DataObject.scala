package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

import DataTheme.DataTheme
import OrganizationClass.OrganizationClass
import spray.json.JsValue

object DataTheme extends Enumeration{
	val Atmosphere, Ecosystem, Ocean, CP, CAL, Other = Value
	type DataTheme = Value
}

object OrganizationClass extends Enumeration{
	val Org, CF, TC, Station, AS, ES, OS = Value
	type OrganizationClass = Value
}

case class UriResource(uri: URI, label: Option[String])

case class TimeInterval(start: Instant, stop: Instant)

case class Organization(self: UriResource, orgClass: OrganizationClass)

case class Station(
	org: Organization,
	id: String,
	name: String,
	theme: DataTheme,
	pos: Option[Position],
	coverage: Option[String]
)

case class DataObjectSpec(
	self: UriResource,
	format: UriResource,
	encoding: UriResource,
	dataLevel: Int,
	datasetSpec: Option[JsValue]
)

case class DataAcquisition(station: Station, interval: Option[TimeInterval])
case class DataProduction(
	creator: UriResource,
	contributors: Seq[UriResource],
	host: Option[Organization],
	comment: Option[String],
	dateTime: Instant
)
case class DataSubmission(submitter: Organization, start: Instant, stop: Option[Instant])

case class TemporalCoverage(interval: TimeInterval, resolution: Option[String])

case class L2OrLessSpecificMeta(
	acquisition: DataAcquisition,
	productionInfo: Option[DataProduction],
	nRows: Option[Int]
)
case class L3SpecificMeta(
	title: String,
	description: Option[String],
	spatial: SpatialCoverage,
	temporal: TemporalCoverage,
	productionInfo: DataProduction,
	theme: DataTheme
)

sealed trait DataAffiliation
case object Icos extends DataAffiliation
case class OrgAffiliation(org: UriResource) extends DataAffiliation

case class DataObject(
	hash: Sha256Sum,
	accessUrl: Option[URI],
	pid: Option[String],
	fileName: Option[String],
	submission: DataSubmission,
	specification: DataObjectSpec,
	specificInfo: Either[L3SpecificMeta, L2OrLessSpecificMeta]
){
	def production: Option[DataProduction] = specificInfo.fold(
		l3 => Some(l3.productionInfo),
		l2 => l2.productionInfo
	)
	def coverage: Option[GeoFeature] = specificInfo.fold(
		l3 => Some(l3.spatial),
		l2 => {
			val station = l2.acquisition.station
			station.coverage.map(GeoFeature.apply).orElse(station.pos)
		}
	)
	def theme: DataTheme = specificInfo.fold(_.theme, _.acquisition.station.theme)

	def affiliation: DataAffiliation =
		if(submission.submitter.orgClass == OrganizationClass.TC) Icos
		else {
			val orgOpt = specificInfo.fold(
				l3 => Some(l3.productionInfo),
				l2 => l2.productionInfo
			).flatMap(_.host).map(_.self)
			OrgAffiliation(orgOpt.getOrElse(submission.submitter.self))
		}
}
