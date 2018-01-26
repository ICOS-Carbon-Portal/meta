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

sealed trait Agent{
	val self: UriResource
}
case class Organization(self: UriResource, name: String, orgClass: OrganizationClass) extends Agent
case class Person(self: UriResource, firstName: String, lastName: String) extends Agent


case class Station(
	org: Organization,
	id: String,
	name: String,
	theme: DataTheme,
	coverage: Option[GeoFeature]
)

case class DataObjectSpec(
	self: UriResource,
	format: UriResource,
	encoding: UriResource,
	dataLevel: Int,
	datasetSpec: Option[JsValue]
)

case class DataAcquisition(
	station: Station,
	interval: Option[TimeInterval],
	instrument: Option[URI],
	samplingHeight: Option[Float]
)

case class DataProduction(
	creator: Agent,
	contributors: Seq[Agent],
	host: Option[Organization],
	comment: Option[String],
	dateTime: Instant
)
case class DataSubmission(submitter: Organization, start: Instant, stop: Option[Instant])

case class TemporalCoverage(interval: TimeInterval, resolution: Option[String])

case class L2OrLessSpecificMeta(
	acquisition: DataAcquisition,
	productionInfo: Option[DataProduction],
	nRows: Option[Int],
	coverage: Option[GeoFeature]
)

case class L3SpecificMeta(
	title: String,
	description: Option[String],
	spatial: LatLonBox,
	temporal: TemporalCoverage,
	productionInfo: DataProduction,
	theme: DataTheme
)

sealed trait DataAffiliation
case object Icos extends DataAffiliation
case class OrgAffiliation(org: Organization) extends DataAffiliation

case class DataObject(
	hash: Sha256Sum,
	accessUrl: Option[URI],
	pid: Option[String],
	doi: Option[String],
	fileName: String,
	size: Option[Long],
	submission: DataSubmission,
	specification: DataObjectSpec,
	specificInfo: Either[L3SpecificMeta, L2OrLessSpecificMeta],
	previousVersion: Option[URI],
	nextVersion: Option[URI]
){
	def production: Option[DataProduction] = specificInfo.fold(
		l3 => Some(l3.productionInfo),
		l2 => l2.productionInfo
	)
	def coverage: Option[GeoFeature] = specificInfo.fold(
		l3 => Some(l3.spatial),
		l2 => l2.coverage.orElse(l2.acquisition.station.coverage)
	)
	def theme: DataTheme = specificInfo.fold(_.theme, _.acquisition.station.theme)

	def affiliation: DataAffiliation =
		if(submission.submitter.orgClass == OrganizationClass.TC) Icos
		else {
			val orgOpt = specificInfo.fold(
				l3 => Some(l3.productionInfo),
				l2 => l2.productionInfo
			).flatMap(_.host)
			OrgAffiliation(orgOpt.getOrElse(submission.submitter))
		}
}

sealed trait DataItem

sealed trait StaticDataItem extends DataItem

final case class PlainDataObject(res: URI, name: String) extends StaticDataItem

sealed trait DataItemCollection extends DataItem {
	type M <: DataItem
	def members: Seq[M]
	def creator: Organization
	def title: String
	def description: Option[String]
}

final case class StaticCollection(
	res: URI,
	members: Seq[StaticDataItem],
	creator: Organization,
	title: String,
	description: Option[String]
) extends DataItemCollection with StaticDataItem {
	type M = StaticDataItem
}
