package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.OntoConstants.*
import se.lu.nateko.cp.meta.api.CustomVocab

import java.net.URI

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	private given BaseUriProvider = makeUriProvider(CpmetaVocab.MetaPrefix)

//	val stationClass = getRelativeRaw("Station")
	val ingosStationClass = getRelativeRaw("IngosStation")
	val wdcggStationClass = getRelativeRaw("WdcggStation")
	val fluxnetStationClass = getRelativeRaw("FluxnetStation")
	val atmoDroughtStationClass = getRelativeRaw("AtmoStation")
	val sailDroneStationClass = getRelativeRaw("SailDrone")
	val neonStationClass = getRelativeRaw("NeonStation")
	val cityStationClass = getRelativeRaw("IcosCitiesStation")

	val atmoStationClass = getRelativeRaw("AS")
	val ecoStationClass = getRelativeRaw("ES")
	val oceStationClass = getRelativeRaw("OS")
	val munichStationClass = getRelativeRaw("MunichMidLow")
	val parisStationClass = getRelativeRaw("ParisMidLow")
	val zurichStationClass = getRelativeRaw("ZurichMidLow")
//	val tcClass = getRelativeRaw("ThematicCenter")
//	val cfClass = getRelativeRaw("CentralFacility")
	val orgClass = getRelativeRaw("Organization")
	val funderClass = getRelativeRaw("Funder")
	val fundingClass = getRelativeRaw("Funding")
	val membershipClass = getRelativeRaw("Membership")
	val instrumentClass = getRelativeRaw("Instrument")

	val aquisitionClass = getRelativeRaw("DataAcquisition")
	val productionClass = getRelativeRaw("DataProduction")
	val submissionClass = getRelativeRaw("DataSubmission")
	val dataObjectClass = getRelativeRaw("DataObject")
	val docObjectClass = getRelativeRaw("DocumentObject")
	val dataObjectSpecClass = getRelativeRaw("DataObjectSpec")
	val simpleObjectSpecClass = getRelativeRaw("SimpleObjectSpec")
	val datasetSpecClass = getRelativeRaw("DatasetSpec")
	val tabularDatasetSpecClass = getRelativeRaw("TabularDatasetSpec")
	val plainCollectionClass = getRelativeRaw("PlainCollection")
	val collectionClass = getRelativeRaw("Collection")
	val spatialCoverageClass = getRelativeRaw("SpatialCoverage")
	val latLonBoxClass = getRelativeRaw("LatLonBox")
	val positionClass = getRelativeRaw("Position")
	val variableInfoClass = getRelativeRaw("VariableInfo")

	val stationTimeSeriesDs = getRelativeRaw("stationTimeSeriesDataset")
	val spatioTemporalDs = getRelativeRaw("spatioTemporalDataset")

	val hasElevation = getRelativeRaw("hasElevation")
	val hasLatitude = getRelativeRaw("hasLatitude")
	val hasNorthernBound = getRelativeRaw("hasNorthernBound")
	val hasSouthernBound = getRelativeRaw("hasSouthernBound")
	val hasLongitude = getRelativeRaw("hasLongitude")
	val hasEasternBound = getRelativeRaw("hasEasternBound")
	val hasWesternBound = getRelativeRaw("hasWesternBound")
	val hasSamplingPoint = getRelativeRaw("hasSamplingPoint")
	val hasSamplingHeight = getRelativeRaw("hasSamplingHeight")
	val hasName = getRelativeRaw("hasName")
	val hasStationId = getRelativeRaw("hasStationId")
	val hasStationClass = getRelativeRaw("hasStationClass")
	val hasTimeZoneOffset = getRelativeRaw("hasTimeZoneOffset")
	val hasResponsibleOrganization = getRelativeRaw("hasResponsibleOrganization")
	val countryCode = getRelativeRaw("countryCode")
	val country = getRelativeRaw("country")

	val hasFunding = getRelativeRaw("hasFunding")
	val hasFunder = getRelativeRaw("hasFunder")
	val funderIdentifier = getRelativeRaw("funderIdentifier")
	val funderIdentifierType = getRelativeRaw("funderIdentifierType")
	val awardNumber = getRelativeRaw("awardNumber")
	val awardTitle = getRelativeRaw("awardTitle")
	val awardURI = getRelativeRaw("awardURI")
	val impliesDefaultLicence = getRelativeRaw("impliesDefaultLicence")

	val hasWebpageElements = getRelativeRaw("hasWebpageElements")
	val hasCoverImage = getRelativeRaw("hasCoverImage")
	val hasLinkbox = getRelativeRaw("hasLinkbox")
	val hasWebpageLink = getRelativeRaw("hasWebpageLink")
	val hasOrderWeight = getRelativeRaw("hasOrderWeight")

	val hasSha256sum = getRelativeRaw("hasSha256sum")
	val hasDoi = getRelativeRaw("hasDoi")
	val isNextVersionOf = getRelativeRaw("isNextVersionOf")
	val wasAcquiredBy = getRelativeRaw("wasAcquiredBy")
	val wasSubmittedBy = getRelativeRaw("wasSubmittedBy")
	val wasProducedBy = getRelativeRaw("wasProducedBy")
	val wasPerformedBy = getRelativeRaw("wasPerformedBy")
	val wasPerformedWith = getRelativeRaw("wasPerformedWith")
	val wasParticipatedInBy = getRelativeRaw("wasParticipatedInBy")
	val wasHostedBy = getRelativeRaw("wasHostedBy")
	val hasDataLevel = getRelativeRaw("hasDataLevel")
	val hasDataTheme = getRelativeRaw("hasDataTheme")
	val hasAssociatedProject = getRelativeRaw("hasAssociatedProject")
	val hasDocumentationObject = getRelativeRaw("hasDocumentationObject")
	val hasDocumentationUri = getRelativeRaw("hasDocumentationUri")
	val hasAssociatedPublication = getRelativeRaw("hasAssociatedPublication")
	val containsDataset = getRelativeRaw("containsDataset")
	val hasObjectSpec = getRelativeRaw("hasObjectSpec")
	val hasFormat = getRelativeRaw("hasFormat")
	val hasGoodFlagValue = getRelativeRaw("hasGoodFlagValue")
	val hasEncoding = getRelativeRaw("hasEncoding")
	val hasFormatSpecificMeta = getRelativeRaw("hasFormatSpecificMetadata")
	val hasSpecificDatasetType = getRelativeRaw("hasSpecificDatasetType")
	val hasNumberOfRows = getRelativeRaw("hasNumberOfRows")
	val hasActualColumnNames = getRelativeRaw("hasActualColumnNames")
	val hasActualVariable = getRelativeRaw("hasActualVariable")
	val hasColumn = getRelativeRaw("hasColumn")
	val hasColumnTitle = getRelativeRaw("hasColumnTitle")
	val hasVariable = getRelativeRaw("hasVariable")
	val hasVariableTitle = getRelativeRaw("hasVariableTitle")
	val hasVariableName = getRelativeRaw("hasVariableName")
	val isQualityFlagFor = getRelativeRaw("isQualityFlagFor")
	val hasKeywords = getRelativeRaw("hasKeywords")
	val hasKeyword = getRelativeRaw("hasKeyword")
	val hasSizeInBytes = getRelativeRaw("hasSizeInBytes")
	val hasTemporalResolution = getRelativeRaw("hasTemporalResolution")
	val hasSpatialCoverage = getRelativeRaw("hasSpatialCoverage")
	val asGeoJSON = getRelativeRaw("asGeoJSON")
	val hasCitationString = getRelativeRaw("hasCitationString")
	val hasBiblioInfo = getRelativeRaw("hasBiblioInfo")
	val wasPerformedAt = getRelativeRaw("wasPerformedAt")
	val hasEcosystemType = getRelativeRaw("hasEcosystemType")
	val hasClimateZone = getRelativeRaw("hasClimateZone")
	val hasMeanAnnualTemp = getRelativeRaw("hasMeanAnnualTemp")
	val hasMeanAnnualPrecip = getRelativeRaw("hasMeanAnnualPrecip")
	val hasMeanAnnualRadiation = getRelativeRaw("hasMeanAnnualRadiation")
	val hasOperationalPeriod = getRelativeRaw("hasOperationalPeriod")
	val operatesOn = getRelativeRaw("operatesOn")
	val hasDepiction = getRelativeRaw("hasDepiction")
	val hasValueType = getRelativeRaw("hasValueType")
	val hasValueFormat = getRelativeRaw("hasValueFormat")
	val hasUnit = getRelativeRaw("hasUnit")
	val hasQuantityKind = getRelativeRaw("hasQuantityKind")
	val hasMinValue = getRelativeRaw("hasMinValue")
	val hasMaxValue = getRelativeRaw("hasMaxValue")
	val isOptionalVariable = getRelativeRaw("isOptionalVariable")
	val isOptionalColumn = getRelativeRaw("isOptionalColumn")
	val isRegexVariable = getRelativeRaw("isRegexVariable")
	val isRegexColumn = getRelativeRaw("isRegexColumn")
	val isDiscontinued = getRelativeRaw("isDiscontinued")

	val personClass = getRelativeRaw("Person")
	val roleClass = getRelativeRaw("Role")

	val hasFirstName = getRelativeRaw("hasFirstName")
	val hasLastName = getRelativeRaw("hasLastName")
	val hasEmail = getRelativeRaw("hasEmail")
	val hasOrcidId = getRelativeRaw("hasOrcidId")
	val hasRole = getRelativeRaw("hasRole")
	val hasMembership = getRelativeRaw("hasMembership")
	val atOrganization = getRelativeRaw("atOrganization")
	val hasExtraRoleInfo = getRelativeRaw("hasExtraRoleInfo")
	val hasAttributionWeight = getRelativeRaw("hasAttributionWeight")
	val hasStartTime = getRelativeRaw("hasStartTime")
	val hasEndDate = getRelativeRaw("hasEndDate")
	val hasStartDate = getRelativeRaw("hasStartDate")
	val hasEndTime = getRelativeRaw("hasEndTime")
	val hasInstrumentOwner = getRelativeRaw("hasInstrumentOwner")
	val hasInstrumentComponent = getRelativeRaw("hasInstrumentComponent")
	val hasVendor = getRelativeRaw("hasVendor")
	val hasModel = getRelativeRaw("hasModel")
	val hasSerialNumber = getRelativeRaw("hasSerialNumber")
	val hasIcon = getRelativeRaw("hasIcon")
	val hasMarkerIcon = getRelativeRaw("hasMarkerIcon")

	val hasAtcId = getRelativeRaw("hasAtcId")
	val hasEtcId = getRelativeRaw("hasEtcId")
	val hasOtcId = getRelativeRaw("hasOtcId")
	val hasMunichId = getRelativeRaw("hasMunichId")
	val hasParisId = getRelativeRaw("hasParisId")
	val hasZurichId = getRelativeRaw("hasZurichId")
	val belongsToNetwork = getRelativeRaw("belongsToNetwork")

	val hasWigosId = getRelativeRaw("hasWigosId")

	val ancillaryValueClass = getRelativeRaw("AncillaryValue")
	val ancillaryEntryClass = getRelativeRaw("AncillaryEntry")

	val hasAncillaryDataValue = getRelativeRaw("hasAncillaryDataValue")
	val hasAncillaryObjectValue = getRelativeRaw("hasAncillaryObjectValue")
	val hasAncillaryEntry = getRelativeRaw("hasAncillaryEntry")

	val wdcggFormat = getRelativeRaw("asciiWdcggTimeSer")
	val atcProductFormat = getRelativeRaw("asciiAtcProductTimeSer")
	val asciiAtcFlaskTimeSer = getRelativeRaw("asciiAtcFlaskTimeSer")
	val netCDFTimeSeriesFormat = getRelativeRaw(netCdfTsFormatSuff)
	val netCDFSpatialFormat = getRelativeRaw(netCdfFormatSuff)
	val microsoftExcelFormat = getRelativeRaw(excelFormatSuff)


	object prov extends CustomVocab {
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.ProvPrefix)

		val wasAssociatedWith = getRelativeRaw("wasAssociatedWith")
		val hadPrimarySource = getRelativeRaw("hadPrimarySource")
		val startedAtTime = getRelativeRaw("startedAtTime")
		val endedAtTime = getRelativeRaw("endedAtTime")
	}

	object dcterms extends CustomVocab {
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.DctermsPrefix)

		val licenseDocClass = getRelativeRaw("LicenseDocument")

		val date = getRelativeRaw("date")
		val title = getRelativeRaw("title")
		val description = getRelativeRaw("description")
		val creator = getRelativeRaw("creator")
		val hasPart = getRelativeRaw("hasPart")
		val dateSubmitted = getRelativeRaw("dateSubmitted")
		val license = getRelativeRaw("license")
	}

	object ssn extends CustomVocab{
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.SsnPrefix)

		val deploymentClass = getRelativeRaw("Deployment")
		val forProperty = getRelativeRaw("forProperty")
		val hasDeployment = getRelativeRaw("hasDeployment")
	}

	object sites extends CustomVocab {
		val factory = top.factory
		private given BaseUriProvider = makeUriProvider(CpmetaVocab.SitesPrefix)
		val stationClass = getRelativeRaw("Station")
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
