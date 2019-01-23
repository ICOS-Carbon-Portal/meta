package se.lu.nateko.cp.meta

import java.net.URI
import java.time.Instant

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._


case class UploadMetadataDto(
	hashSum: Sha256Sum,
	submitterId: String,
	objectSpecification: URI,
	fileName: String,
	specificInfo: Either[ElaboratedProductMetadata, StationDataMetadata],
	isNextVersionOf: Option[Sha256Sum],
	preExistingDoi: Option[String]
)

case class StaticCollectionDto(
	submitterId: String,
	members: Seq[URI],
	title: String,
	description: Option[String],
	isNextVersionOf: Option[Sha256Sum],
	preExistingDoi: Option[String]
)

case class StationDataMetadata(
	station: URI,
	instrument: Option[Either[URI, Seq[URI]]],
	samplingHeight: Option[Float],
	acquisitionInterval: Option[TimeInterval],
	nRows: Option[Int],
	production: Option[DataProductionDto]
){
	def instruments: Seq[URI] = instrument.fold(Seq.empty[URI])(_.fold(Seq(_), identity))
}

case class ElaboratedProductMetadata(
	title: String,
	description: Option[String],
	spatial: Either[LatLonBox, URI],
	temporal: TemporalCoverage,
	production: DataProductionDto,
	customLandingPage: Option[URI]
)

case class DataProductionDto(
	creator: URI,
	contributors: Seq[URI],
	hostOrganization: Option[URI],
	comment: Option[String],
	creationDate: Instant
)

case class SubmitterProfile(id: String, producingOrganizationClass: Option[URI])