package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

case class UriResource(uri: URI, label: Option[String], comments: Seq[String])

sealed trait Agent{
	val self: UriResource
}
case class Organization(self: UriResource, name: String, email: Option[String]) extends Agent
case class Person(self: UriResource, firstName: String, lastName: String, orcid: Option[Orcid]) extends Agent


case class Station(
	org: Organization,
	id: String,
	name: String,
	coverage: Option[GeoFeature],
	responsibleOrganization: Option[Organization],
	sites: Option[Seq[Site]],
	ecosystems: Option[Seq[UriResource]],
	climateZone: Option[UriResource],
	meanAnnualTemp: Option[Float],
	operationalPeriod: Option[String],
	website: Option[URI],
	pictures: Option[Seq[URI]]
)

case class Location(geometry: GeoFeature, label: Option[String])
case class Site(self: UriResource, ecosystem: UriResource, location: Option[Location])

case class Project(self: UriResource, keywords: Option[Seq[String]])
case class DataTheme(self: UriResource, icon: URI, markerIcon: Option[URI])

case class DataObjectSpec(
	self: UriResource,
	project: Project,
	theme: DataTheme,
	format: UriResource,
	encoding: UriResource,
	dataLevel: Int,
	datasetSpec: Option[UriResource],
	documentation: Seq[PlainStaticObject],
	keywords: Option[Seq[String]]
)

case class DataAcquisition(
	station: Station,
	site: Option[Site],
	interval: Option[TimeInterval],
	instrument: OptionalOneOrSeq[URI],
	samplingPoint: Option[Position],
	samplingHeight: Option[Float]
){
	def instruments: Seq[URI] = instrument.fold(Seq.empty[URI])(_.fold(Seq(_), identity))
	def coverage: Option[GeoFeature] = samplingPoint
		.orElse(site.flatMap(_.location.map(_.geometry)))
		.orElse(station.coverage)
}

case class DataProduction(
	creator: Agent,
	contributors: Seq[Agent],
	host: Option[Organization],
	comment: Option[String],
	sources: Seq[UriResource],
	dateTime: Instant
)
case class DataSubmission(submitter: Organization, start: Instant, stop: Option[Instant])

case class L2OrLessSpecificMeta(
	acquisition: DataAcquisition,
	productionInfo: Option[DataProduction],
	nRows: Option[Int],
	coverage: Option[GeoFeature],
	columns: Option[Seq[ColumnInfo]]
)

case class ValueType(self: UriResource, quantityKind: Option[UriResource], unit: Option[String])
case class L3VarInfo(label: String, valueType: ValueType, minMax: Option[(Double, Double)])
case class ColumnInfo(label: String, valueType: ValueType)

case class L3SpecificMeta(
	title: String,
	description: Option[String],
	spatial: LatLonBox,
	temporal: TemporalCoverage,
	productionInfo: DataProduction,
	variables: Option[Seq[L3VarInfo]]
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
	def references: References

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
	references: References
) extends StaticObject{
	def production: Option[DataProduction] = specificInfo.fold(
		l3 => Some(l3.productionInfo),
		l2 => l2.productionInfo
	)

	def coverage: Option[GeoFeature] = specificInfo.fold(
		l3 => Some(l3.spatial),
		l2 => l2.coverage.orElse(l2.acquisition.coverage)
	)

	def keywords: Option[Seq[String]] =
		Option((references.keywords ++ specification.keywords ++ specification.project.keywords).flatten)
			.filter(_.nonEmpty)
			.map(_.toSeq.sorted)
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
	parentCollections: Seq[UriResource],
	references: References
) extends StaticObject

case class References(citationString: Option[String], keywords: Option[Seq[String]], authors: Option[Seq[Person]])
