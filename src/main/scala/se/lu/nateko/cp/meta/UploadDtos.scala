package se.lu.nateko.cp.meta

import java.net.URI
import java.time.Instant

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.doi.*

sealed trait UploadDto{
	def submitterId: String
	def isNextVersionOf: OptionalOneOrSeq[Sha256Sum]
	def preExistingDoi: Option[Doi]
}

sealed trait ObjectUploadDto extends UploadDto {
	def hashSum: Sha256Sum
	def fileName: String
	def references: Option[ReferencesDto]
	def duplicateFilenameAllowed = references.flatMap(_.duplicateFilenameAllowed).getOrElse(false)
	def autodeprecateSameFilenameObjects = references.flatMap(_.autodeprecateSameFilenameObjects).getOrElse(false)
	def partialUpload = references.flatMap(_.partialUpload).getOrElse(false)
}

case class DataObjectDto(
	hashSum: Sha256Sum,
	submitterId: String,
	objectSpecification: URI,
	fileName: String,
	specificInfo: Either[SpatioTemporalDto, StationTimeSeriesDto],
	isNextVersionOf: OptionalOneOrSeq[Sha256Sum],
	preExistingDoi: Option[Doi],
	references: Option[ReferencesDto]
) extends ObjectUploadDto

case class DocObjectDto(
	hashSum: Sha256Sum,
	submitterId: String,
	fileName: String,
	title: Option[String],
	description: Option[String],
	authors: Seq[URI],
	isNextVersionOf: OptionalOneOrSeq[Sha256Sum],
	preExistingDoi: Option[Doi],
	references: Option[ReferencesDto]
) extends ObjectUploadDto


case class StaticCollectionDto(
	submitterId: String,
	members: Seq[URI],
	title: String,
	description: Option[String],
	isNextVersionOf: OptionalOneOrSeq[Sha256Sum],
	preExistingDoi: Option[Doi],
	documentation: Option[Sha256Sum]
) extends UploadDto

case class StationTimeSeriesDto(
	station: URI,
	site: Option[URI],
	instrument: OptionalOneOrSeq[URI],
	samplingPoint: Option[Position],
	samplingHeight: Option[Float],
	acquisitionInterval: Option[TimeInterval],
	nRows: Option[Int],
	production: Option[DataProductionDto]
){
	def instruments: Seq[URI] = instrument.fold(Seq.empty[URI])(_.fold(Seq(_), identity))
}

case class SpatioTemporalDto(
	title: String,
	description: Option[String],
	spatial: Either[GeoFeature, URI],
	temporal: TemporalCoverage,
	production: DataProductionDto,
	forStation: Option[URI],
	samplingHeight: Option[Float],
	customLandingPage: Option[URI],
	variables: Option[Seq[String]]
)

case class DataProductionDto(
	creator: URI,
	contributors: Seq[URI],
	hostOrganization: Option[URI],
	comment: Option[String],
	sources: Option[Seq[Sha256Sum]],
	documentation: Option[Sha256Sum],
	creationDate: Instant
)

case class SubmitterProfile(
	id: String,
	producingOrganizationClass: Option[URI],
	producingOrganization: Option[URI],
	authorizedThemes: Option[Seq[URI]],
	authorizedProjects: Option[Seq[URI]]
)

case class ReferencesDto(
	keywords: Option[Seq[String]],
	licence: Option[URI],
	moratorium: Option[Instant],
	duplicateFilenameAllowed: Option[Boolean],
	autodeprecateSameFilenameObjects: Option[Boolean],
	partialUpload: Option[Boolean]
)
