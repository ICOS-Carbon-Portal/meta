package se.lu.nateko.cp.meta.services

import java.net.URI
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.OntoConstants.*
import se.lu.nateko.cp.meta.api.CustomVocab

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	private given BaseUriProvider = makeUriProvider(CpmetaVocab.MetaPrefix)

//	val stationClass = getRelativeRaw("Station")
	val ingosStationClass: IRI = getRelativeRaw("IngosStation")
	val wdcggStationClass: IRI = getRelativeRaw("WdcggStation")
	val fluxnetStationClass: IRI = getRelativeRaw("FluxnetStation")
	val atmoDroughtStationClass: IRI = getRelativeRaw("AtmoStation")
	val sailDroneStationClass: IRI = getRelativeRaw("SailDrone")
	val neonStationClass: IRI = getRelativeRaw("NeonStation")
	val cityStationClass: IRI = getRelativeRaw("IcosCitiesStation")

	val atmoStationClass: IRI = getRelativeRaw("AS")
	val ecoStationClass: IRI = getRelativeRaw("ES")
	val oceStationClass: IRI = getRelativeRaw("OS")
	val munichStationClass: IRI = getRelativeRaw("MunichMidLow")
	val parisStationClass: IRI = getRelativeRaw("ParisMidLow")
	val zurichStationClass: IRI = getRelativeRaw("ZurichMidLow")
//	val tcClass = getRelativeRaw("ThematicCenter")
//	val cfClass = getRelativeRaw("CentralFacility")
	val orgClass: IRI = getRelativeRaw("Organization")
	val funderClass: IRI = getRelativeRaw("Funder")
	val fundingClass: IRI = getRelativeRaw("Funding")
	val membershipClass: IRI = getRelativeRaw("Membership")
	val instrumentClass: IRI = getRelativeRaw("Instrument")

	val aquisitionClass: IRI = getRelativeRaw("DataAcquisition")
	val productionClass: IRI = getRelativeRaw("DataProduction")
	val submissionClass: IRI = getRelativeRaw("DataSubmission")
	val dataObjectClass: IRI = getRelativeRaw("DataObject")
	val docObjectClass: IRI = getRelativeRaw("DocumentObject")
	val dataObjectSpecClass: IRI = getRelativeRaw("DataObjectSpec")
	val datasetSpecClass: IRI = getRelativeRaw("DatasetSpec")
	val tabularDatasetSpecClass: IRI = getRelativeRaw("TabularDatasetSpec")
	val plainCollectionClass: IRI = getRelativeRaw("PlainCollection")
	val collectionClass: IRI = getRelativeRaw("Collection")
	val spatialCoverageClass: IRI = getRelativeRaw("SpatialCoverage")
	val latLonBoxClass: IRI = getRelativeRaw("LatLonBox")
	val positionClass: IRI = getRelativeRaw("Position")
	val variableInfoClass: IRI = getRelativeRaw("VariableInfo")

	val stationTimeSeriesDs: IRI = getRelativeRaw("stationTimeSeriesDataset")
	val spatioTemporalDs: IRI = getRelativeRaw("spatioTemporalDataset")

	val hasElevation: IRI = getRelativeRaw("hasElevation")
	val hasLatitude: IRI = getRelativeRaw("hasLatitude")
	val hasNorthernBound: IRI = getRelativeRaw("hasNorthernBound")
	val hasSouthernBound: IRI = getRelativeRaw("hasSouthernBound")
	val hasLongitude: IRI = getRelativeRaw("hasLongitude")
	val hasEasternBound: IRI = getRelativeRaw("hasEasternBound")
	val hasWesternBound: IRI = getRelativeRaw("hasWesternBound")
	val hasSamplingPoint: IRI = getRelativeRaw("hasSamplingPoint")
	val hasSamplingHeight: IRI = getRelativeRaw("hasSamplingHeight")
	val hasName: IRI = getRelativeRaw("hasName")
	val hasStationId: IRI = getRelativeRaw("hasStationId")
	val hasStationClass: IRI = getRelativeRaw("hasStationClass")
	val hasTimeZoneOffset: IRI = getRelativeRaw("hasTimeZoneOffset")
	val hasResponsibleOrganization: IRI = getRelativeRaw("hasResponsibleOrganization")
	val countryCode: IRI = getRelativeRaw("countryCode")
	val country: IRI = getRelativeRaw("country")

	val hasFunding: IRI = getRelativeRaw("hasFunding")
	val hasFunder: IRI = getRelativeRaw("hasFunder")
	val funderIdentifier: IRI = getRelativeRaw("funderIdentifier")
	val funderIdentifierType: IRI = getRelativeRaw("funderIdentifierType")
	val awardNumber: IRI = getRelativeRaw("awardNumber")
	val awardTitle: IRI = getRelativeRaw("awardTitle")
	val awardURI: IRI = getRelativeRaw("awardURI")
	val impliesDefaultLicence: IRI = getRelativeRaw("impliesDefaultLicence")

	val hasWebpageElements: IRI = getRelativeRaw("hasWebpageElements")
	val hasCoverImage: IRI = getRelativeRaw("hasCoverImage")
	val hasLinkbox: IRI = getRelativeRaw("hasLinkbox")
	val hasWebpageLink: IRI = getRelativeRaw("hasWebpageLink")
	val hasOrderWeight: IRI = getRelativeRaw("hasOrderWeight")

	val hasSha256sum: IRI = getRelativeRaw("hasSha256sum")
	val hasDoi: IRI = getRelativeRaw("hasDoi")
	val isNextVersionOf: IRI = getRelativeRaw("isNextVersionOf")
	val wasAcquiredBy: IRI = getRelativeRaw("wasAcquiredBy")
	val wasSubmittedBy: IRI = getRelativeRaw("wasSubmittedBy")
	val wasProducedBy: IRI = getRelativeRaw("wasProducedBy")
	val wasPerformedBy: IRI = getRelativeRaw("wasPerformedBy")
	val wasPerformedWith: IRI = getRelativeRaw("wasPerformedWith")
	val wasParticipatedInBy: IRI = getRelativeRaw("wasParticipatedInBy")
	val wasHostedBy: IRI = getRelativeRaw("wasHostedBy")
	val hasDataLevel: IRI = getRelativeRaw("hasDataLevel")
	val hasDataTheme: IRI = getRelativeRaw("hasDataTheme")
	val hasAssociatedProject: IRI = getRelativeRaw("hasAssociatedProject")
	val hasDocumentationObject: IRI = getRelativeRaw("hasDocumentationObject")
	val hasDocumentationUri: IRI = getRelativeRaw("hasDocumentationUri")
	val hasAssociatedPublication: IRI = getRelativeRaw("hasAssociatedPublication")
	val containsDataset: IRI = getRelativeRaw("containsDataset")
	val hasObjectSpec: IRI = getRelativeRaw("hasObjectSpec")
	val hasFormat: IRI = getRelativeRaw("hasFormat")
	val hasGoodFlagValue: IRI = getRelativeRaw("hasGoodFlagValue")
	val hasEncoding: IRI = getRelativeRaw("hasEncoding")
	val hasFormatSpecificMeta: IRI = getRelativeRaw("hasFormatSpecificMetadata")
	val hasSpecificDatasetType: IRI = getRelativeRaw("hasSpecificDatasetType")
	val hasNumberOfRows: IRI = getRelativeRaw("hasNumberOfRows")
	val hasActualColumnNames: IRI = getRelativeRaw("hasActualColumnNames")
	val hasActualVariable: IRI = getRelativeRaw("hasActualVariable")
	val hasColumn: IRI = getRelativeRaw("hasColumn")
	val hasColumnTitle: IRI = getRelativeRaw("hasColumnTitle")
	val hasVariable: IRI = getRelativeRaw("hasVariable")
	val hasVariableTitle: IRI = getRelativeRaw("hasVariableTitle")
	val hasVariableName: IRI = getRelativeRaw("hasVariableName")
	val isQualityFlagFor: IRI = getRelativeRaw("isQualityFlagFor")
	val hasKeywords: IRI = getRelativeRaw("hasKeywords")
	val hasKeyword: IRI = getRelativeRaw("hasKeyword")
	val hasSizeInBytes: IRI = getRelativeRaw("hasSizeInBytes")
	val hasTemporalResolution: IRI = getRelativeRaw("hasTemporalResolution")
	val hasSpatialCoverage: IRI = getRelativeRaw("hasSpatialCoverage")
	val asGeoJSON: IRI = getRelativeRaw("asGeoJSON")
	val hasCitationString: IRI = getRelativeRaw("hasCitationString")
	val hasBiblioInfo: IRI = getRelativeRaw("hasBiblioInfo")
	val wasPerformedAt: IRI = getRelativeRaw("wasPerformedAt")
	val hasEcosystemType: IRI = getRelativeRaw("hasEcosystemType")
	val hasClimateZone: IRI = getRelativeRaw("hasClimateZone")
	val hasMeanAnnualTemp: IRI = getRelativeRaw("hasMeanAnnualTemp")
	val hasMeanAnnualPrecip: IRI = getRelativeRaw("hasMeanAnnualPrecip")
	val hasMeanAnnualRadiation: IRI = getRelativeRaw("hasMeanAnnualRadiation")
	val hasOperationalPeriod: IRI = getRelativeRaw("hasOperationalPeriod")
	val operatesOn: IRI = getRelativeRaw("operatesOn")
	val hasDepiction: IRI = getRelativeRaw("hasDepiction")
	val hasValueType: IRI = getRelativeRaw("hasValueType")
	val hasValueFormat: IRI = getRelativeRaw("hasValueFormat")
	val hasUnit: IRI = getRelativeRaw("hasUnit")
	val hasQuantityKind: IRI = getRelativeRaw("hasQuantityKind")
	val hasMinValue: IRI = getRelativeRaw("hasMinValue")
	val hasMaxValue: IRI = getRelativeRaw("hasMaxValue")
	val isOptionalVariable: IRI = getRelativeRaw("isOptionalVariable")
	val isOptionalColumn: IRI = getRelativeRaw("isOptionalColumn")
	val isRegexVariable: IRI = getRelativeRaw("isRegexVariable")
	val isRegexColumn: IRI = getRelativeRaw("isRegexColumn")
	val isDiscontinued: IRI = getRelativeRaw("isDiscontinued")

	val personClass: IRI = getRelativeRaw("Person")
	val roleClass: IRI = getRelativeRaw("Role")

	val hasFirstName: IRI = getRelativeRaw("hasFirstName")
	val hasLastName: IRI = getRelativeRaw("hasLastName")
	val hasEmail: IRI = getRelativeRaw("hasEmail")
	val hasOrcidId: IRI = getRelativeRaw("hasOrcidId")
	val hasRole: IRI = getRelativeRaw("hasRole")
	val hasMembership: IRI = getRelativeRaw("hasMembership")
	val atOrganization: IRI = getRelativeRaw("atOrganization")
	val hasExtraRoleInfo: IRI = getRelativeRaw("hasExtraRoleInfo")
	val hasAttributionWeight: IRI = getRelativeRaw("hasAttributionWeight")
	val hasStartTime: IRI = getRelativeRaw("hasStartTime")
	val hasEndDate: IRI = getRelativeRaw("hasEndDate")
	val hasStartDate: IRI = getRelativeRaw("hasStartDate")
	val hasEndTime: IRI = getRelativeRaw("hasEndTime")
	val hasInstrumentOwner: IRI = getRelativeRaw("hasInstrumentOwner")
	val hasInstrumentComponent: IRI = getRelativeRaw("hasInstrumentComponent")
	val hasVendor: IRI = getRelativeRaw("hasVendor")
	val hasModel: IRI = getRelativeRaw("hasModel")
	val hasSerialNumber: IRI = getRelativeRaw("hasSerialNumber")
	val hasIcon: IRI = getRelativeRaw("hasIcon")
	val hasMarkerIcon: IRI = getRelativeRaw("hasMarkerIcon")

	val hasAtcId: IRI = getRelativeRaw("hasAtcId")
	val hasEtcId: IRI = getRelativeRaw("hasEtcId")
	val hasOtcId: IRI = getRelativeRaw("hasOtcId")
	val hasMunichId: IRI = getRelativeRaw("hasMunichId")
	val hasParisId: IRI = getRelativeRaw("hasParisId")
	val hasZurichId: IRI = getRelativeRaw("hasZurichId")
	val belongsToNetwork: IRI = getRelativeRaw("belongsToNetwork")

	val hasWigosId: IRI = getRelativeRaw("hasWigosId")

	val ancillaryValueClass: IRI = getRelativeRaw("AncillaryValue")
	val ancillaryEntryClass: IRI = getRelativeRaw("AncillaryEntry")

	val hasAncillaryDataValue: IRI = getRelativeRaw("hasAncillaryDataValue")
	val hasAncillaryObjectValue: IRI = getRelativeRaw("hasAncillaryObjectValue")
	val hasAncillaryEntry: IRI = getRelativeRaw("hasAncillaryEntry")

	val wdcggFormat: IRI = getRelativeRaw("asciiWdcggTimeSer")
	val atcProductFormat: IRI = getRelativeRaw("asciiAtcProductTimeSer")
	val asciiAtcFlaskTimeSer: IRI = getRelativeRaw("asciiAtcFlaskTimeSer")
	val netCDFTimeSeriesFormat: IRI = getRelativeRaw(netCdfTsFormatSuff)
	val netCDFSpatialFormat: IRI = getRelativeRaw(netCdfFormatSuff)
	val microsoftExcelFormat: IRI = getRelativeRaw(excelFormatSuff)


	object prov extends CustomVocab {
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.ProvPrefix)

		val wasAssociatedWith: IRI = getRelativeRaw("wasAssociatedWith")
		val hadPrimarySource: IRI = getRelativeRaw("hadPrimarySource")
		val startedAtTime: IRI = getRelativeRaw("startedAtTime")
		val endedAtTime: IRI = getRelativeRaw("endedAtTime")
	}

	object dcterms extends CustomVocab {
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.DctermsPrefix)

		val licenseDocClass: IRI = getRelativeRaw("LicenseDocument")

		val date: IRI = getRelativeRaw("date")
		val title: IRI = getRelativeRaw("title")
		val description: IRI = getRelativeRaw("description")
		val creator: IRI = getRelativeRaw("creator")
		val hasPart: IRI = getRelativeRaw("hasPart")
		val dateSubmitted: IRI = getRelativeRaw("dateSubmitted")
		val license: IRI = getRelativeRaw("license")
	}

	object ssn extends CustomVocab{
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.SsnPrefix)

		val deploymentClass: IRI = getRelativeRaw("Deployment")
		val forProperty: IRI = getRelativeRaw("forProperty")
		val hasDeployment: IRI = getRelativeRaw("hasDeployment")
	}

	object sites extends CustomVocab {
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.SitesPrefix)
		val stationClass: IRI = getRelativeRaw("Station")
	}
}

object CpmetaVocab{
	val MetaPrefix = CpmetaPrefix
	val SitesPrefix = "https://meta.fieldsites.se/ontologies/sites/"
	val ProvPrefix = "http://www.w3.org/ns/prov#"
	val DctermsPrefix = "http://purl.org/dc/terms/"
	//val SosaPrefix = "http://www.w3.org/ns/sosa/"
	val SsnPrefix = "http://www.w3.org/ns/ssn/"

	val icosMultiImageZipSuff = "multiImageZip"
	val sitesMultiImageZipSuff = "image"

	def icosMultiImageZipUri = new URI(MetaPrefix + icosMultiImageZipSuff)
	def sitesMultiImageZipUri = new URI(SitesPrefix + sitesMultiImageZipSuff)
}
