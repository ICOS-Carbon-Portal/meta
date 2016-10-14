package se.lu.nateko.cp.meta

import java.net.URI
import java.time.Instant
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._

case class ResourceDto(displayName: String, uri: URI, comment: Option[String])

sealed trait ValueDto
case class LiteralValueDto(value: String, property: ResourceDto) extends ValueDto
case class ObjectValueDto(value: ResourceDto, property: ResourceDto) extends ValueDto

sealed trait DataRestrictionDto
case class MinRestrictionDto(minValue: Double) extends DataRestrictionDto
case class MaxRestrictionDto(maxValue: Double) extends DataRestrictionDto
case class RegexpRestrictionDto(regexp: String) extends DataRestrictionDto
case class OneOfRestrictionDto(values: Seq[String]) extends DataRestrictionDto

case class DataRangeDto(dataType: URI, restrictions: Seq[DataRestrictionDto])
case class CardinalityDto(min: Option[Int], max: Option[Int])

sealed trait PropertyDto
case class DataPropertyDto(resource: ResourceDto, cardinality: CardinalityDto, range: DataRangeDto) extends PropertyDto
case class ObjectPropertyDto(resource: ResourceDto, cardinality: CardinalityDto, range: ResourceDto) extends PropertyDto

case class ClassDto(resource: ResourceDto, properties: Seq[PropertyDto])
case class ClassInfoDto(displayName: String, uri: URI, newInstanceBaseUri: Option[URI]){
	def withFallbackBaseUri(fallback: URI) =
		if(newInstanceBaseUri.isDefined) this
		else this.copy(newInstanceBaseUri = Some(fallback))
}
case class IndividualDto(resource: ResourceDto, owlClass: ClassDto, values: Seq[ValueDto])

case class UpdateDto(isAssertion: Boolean, subject: URI, predicate: URI, obj: String)
case class ReplaceDto(subject: URI, predicate: URI, oldObject: String, newObject: String){
	def assertion = UpdateDto(true, subject, predicate, newObject)
	def retraction = UpdateDto(false, subject, predicate, oldObject)
}

case class UploadMetadataDto(
	hashSum: Sha256Sum,
	submitterId: String,
	objectSpecification: URI,
	fileName: Option[String],
	specificInfo: Either[ElaboratedProductMetadata, StationDataMetadata]
)

case class StationDataMetadata(
	station: URI,
	acquisitionInterval: Option[TimeInterval],
	nRows: Option[Int],
	production: Option[DataProductionDto]
)
case class ElaboratedProductMetadata(
	title: String,
	description: Option[String],
	spatial: Either[SpatialCoverage, URI],
	temporal: TemporalCoverage,
	production: DataProductionDto,
	customLandingPage: Option[URI]
)

case class DataProductionDto(
	creator: URI,
	contributors: Seq[URI],
	hostOrganization: Option[URI],
	creationDate: Instant
)

case class FileDeletionDto(stationUri: URI, file: URI)
case class LabelingUserDto(
	uri: Option[URI],
	mail: String,
	isPi: Boolean,
	tcs: Seq[URI],
	firstName: Option[String],
	lastName: Option[String],
	affiliation: Option[String] = None,
	phone: Option[String] = None
)

case class LabelingStatusUpdate(stationUri: URI, newStatus: String)
