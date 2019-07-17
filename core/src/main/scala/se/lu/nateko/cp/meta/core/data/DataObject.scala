package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

import spray.json.JsValue

case class UriResource(uri: URI, label: Option[String])

sealed trait Agent{
	val self: UriResource
}
case class Organization(self: UriResource, name: String) extends Agent
case class Person(self: UriResource, firstName: String, lastName: String) extends Agent


case class Station(
	org: Organization,
	id: String,
	name: String,
	coverage: Option[GeoFeature]
)

case class DataTheme(self: UriResource, icon: URI, markerIcon: Option[URI])

case class DataObjectSpec(
	self: UriResource,
	project: UriResource,
	theme: DataTheme,
	format: UriResource,
	encoding: UriResource,
	dataLevel: Int,
	datasetSpec: Option[JsValue]
)

case class DataAcquisition(
	station: Station,
	interval: Option[TimeInterval],
	instrument: OptionalOneOrSeq[URI],
	samplingHeight: Option[Float]
){
	def instruments: Seq[URI] = instrument.fold(Seq.empty[URI])(_.fold(Seq(_), identity))
}

case class DataProduction(
	creator: Agent,
	contributors: Seq[Agent],
	host: Option[Organization],
	comment: Option[String],
	sources: Seq[URI],
	dateTime: Instant
)
case class DataSubmission(submitter: Organization, start: Instant, stop: Option[Instant])

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
	productionInfo: DataProduction
)

sealed trait StaticObject{
	def hash: Sha256Sum
	def accessUrl: Option[URI]
	def pid: Option[String]
	def doi: Option[String]
	def fileName: String
	def submission: DataSubmission
	def previousVersion: OptionalOneOrSeq[URI]
	def nextVersion: Option[URI]
	def parentCollections: Seq[UriResource]

	def asDataObject: Option[DataObject] = this match{
		case dobj: DataObject => Some(dobj)
		case _ => None
	}
}

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
	previousVersion: OptionalOneOrSeq[URI],
	nextVersion: Option[URI],
	parentCollections: Seq[UriResource],
	citationString: Option[String]
) extends StaticObject{
	def production: Option[DataProduction] = specificInfo.fold(
		l3 => Some(l3.productionInfo),
		l2 => l2.productionInfo
	)

	def coverage: Option[GeoFeature] = specificInfo.fold(
		l3 => Some(l3.spatial),
		l2 => l2.coverage.orElse(l2.acquisition.station.coverage)
	)
}

case class DocObject(
	hash: Sha256Sum,
	accessUrl: Option[URI],
	pid: Option[String],
	doi: Option[String],
	fileName: String,
	size: Option[Long],
	submission: DataSubmission,
	previousVersion: OptionalOneOrSeq[URI],
	nextVersion: Option[URI],
	parentCollections: Seq[UriResource]
) extends StaticObject
