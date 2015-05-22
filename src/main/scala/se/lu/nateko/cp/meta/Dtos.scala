package se.lu.nateko.cp.meta

import java.net.URI

case class ResourceDto(displayName: String, uri: URI)

sealed trait ValueDto
case class LiteralValueDto(presentation: String, property: ResourceDto) extends ValueDto
case class ObjectValueDto(value: ResourceDto, property: ResourceDto) extends ValueDto

sealed trait DataRestrictionDto
case class MinRestrictionDto(minValue: Double) extends DataRestrictionDto
case class MaxRestrictionDto(maxValue: Double) extends DataRestrictionDto
case class RegexpRestrictionDto(regexp: String) extends DataRestrictionDto

case class DataRangeDto(dataType: String, restrictions: Seq[DataRestrictionDto])

sealed trait PropertyDto
case class ObjectPropertyDto(resource: ResourceDto, isRequired: Boolean, range: DataRangeDto) extends PropertyDto
case class DataPropertyDto(resource: ResourceDto, isRequired: Boolean, rangeClasses: Seq[ResourceDto]) extends PropertyDto

case class ClassDto(resource: ResourceDto, properties: Seq[PropertyDto])
case class IndividualDto(resource: ResourceDto, owlClass: ClassDto, values: Seq[ValueDto])