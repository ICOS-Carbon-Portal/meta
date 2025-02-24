package se.lu.nateko.cp.meta

import se.lu.nateko.cp.doi.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*

import java.net.URI
import java.time.Instant

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

final case class DataObjectDto(
	hashSum: Sha256Sum,
	submitterId: String,
	objectSpecification: URI,
	fileName: String,
	specificInfo: Either[SpatioTemporalDto, StationTimeSeriesDto],
	isNextVersionOf: OptionalOneOrSeq[Sha256Sum],
	preExistingDoi: Option[Doi],
	references: Option[ReferencesDto]
) extends ObjectUploadDto

final case class DocObjectDto(
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


opaque type GeoJsonString <: String = String
object GeoJsonString:
	inline def unsafe(s: String): GeoJsonString = s

type GeoCoverage = GeoFeature | URI | GeoJsonString

final case class StaticCollectionDto(
	submitterId: String,
	members: Seq[URI],
	title: String,
	description: Option[String],
	isNextVersionOf: OptionalOneOrSeq[Sha256Sum],
	preExistingDoi: Option[Doi],
	documentation: Option[Sha256Sum],
	coverage: Option[GeoCoverage]
) extends UploadDto

final case class StationTimeSeriesDto(
	station: URI,
	site: Option[URI],
	instrument: OptionalOneOrSeq[URI],
	samplingPoint: Option[Position],
	samplingHeight: Option[Float],
	acquisitionInterval: Option[TimeInterval],
	nRows: Option[Int],
	production: Option[DataProductionDto],
	spatial: Option[GeoCoverage]
){
	def instruments: Seq[URI] = instrument.fold(Seq.empty[URI])(_.fold(Seq(_), identity))
}

final case class SpatioTemporalDto(
	title: String,
	description: Option[String],
	spatial: GeoCoverage,
	temporal: TemporalCoverage,
	production: DataProductionDto,
	forStation: Option[URI],
	samplingHeight: Option[Float],
	customLandingPage: Option[URI],
	variables: Option[Seq[String]]
)

final case class DataProductionDto(
	creator: URI,
	contributors: Seq[URI],
	hostOrganization: Option[URI],
	comment: Option[String],
	sources: Option[Seq[Sha256Sum]],
	documentation: Option[Sha256Sum],
	creationDate: Instant
)

final case class SubmitterProfile(
	id: String,
	producingOrganizationClass: Option[URI],
	producingOrganization: Option[URI],
	authorizedThemes: Option[Seq[URI]],
	authorizedProjects: Option[Seq[URI]]
)

final case class ReferencesDto(
	keywords: Option[Seq[String]],
	licence: Option[URI],
	moratorium: Option[Instant],
	duplicateFilenameAllowed: Option[Boolean],
	autodeprecateSameFilenameObjects: Option[Boolean],
	partialUpload: Option[Boolean]
)
