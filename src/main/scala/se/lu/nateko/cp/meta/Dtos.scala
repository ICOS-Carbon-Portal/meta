package se.lu.nateko.cp.meta

import java.net.URI

final case class ResourceDto(displayName: String, uri: URI, comment: Option[String])

sealed trait ValueDto
final case class LiteralValueDto(value: String, property: ResourceDto) extends ValueDto
final case class ObjectValueDto(value: ResourceDto, property: ResourceDto) extends ValueDto

sealed trait DataRestrictionDto
final case class MinRestrictionDto(minValue: Double) extends DataRestrictionDto
final case class MaxRestrictionDto(maxValue: Double) extends DataRestrictionDto
final case class RegexpRestrictionDto(regexp: String) extends DataRestrictionDto
final case class OneOfRestrictionDto(values: Seq[String]) extends DataRestrictionDto

final case class DataRangeDto(dataType: URI, restrictions: Seq[DataRestrictionDto])
final case class CardinalityDto(min: Option[Int], max: Option[Int])

sealed trait PropertyDto
final case class DataPropertyDto(resource: ResourceDto, cardinality: CardinalityDto, range: DataRangeDto) extends PropertyDto
final case class ObjectPropertyDto(resource: ResourceDto, cardinality: CardinalityDto) extends PropertyDto

final case class ClassDto(resource: ResourceDto, properties: Seq[PropertyDto])
final case class ClassInfoDto(displayName: String, uri: URI, newInstanceBaseUri: Option[URI]){
	def withFallbackBaseUri(fallback: URI): ClassInfoDto =
		if(newInstanceBaseUri.isDefined) this
		else this.copy(newInstanceBaseUri = Some(fallback))
}
final case class IndividualDto(resource: ResourceDto, owlClass: ClassDto, values: Seq[ValueDto])

final case class UpdateDto(isAssertion: Boolean, subject: URI, predicate: URI, obj: String)
final case class ReplaceDto(subject: URI, predicate: URI, oldObject: String, newObject: String){
	def assertion: UpdateDto = UpdateDto(true, subject, predicate, newObject)
	def retraction: UpdateDto = UpdateDto(false, subject, predicate, oldObject)
}

final case class FileDeletionDto(stationUri: URI, file: URI)
final case class LabelingUserDto(
	uri: Option[URI],
	mail: String,
	isPi: Boolean,
	isDg: Boolean,
	tcs: Seq[URI],
	firstName: Option[String],
	lastName: Option[String],
	affiliation: Option[String] = None,
	phone: Option[String] = None
)

final case class LabelingStatusUpdate(stationUri: URI, newStatus: String, newStatusComment: Option[String])
