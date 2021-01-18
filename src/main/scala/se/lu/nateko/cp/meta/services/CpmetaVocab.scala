package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	implicit val bup = makeUriProvider(CpmetaVocab.MetaPrefix)

//	val stationClass = getRelativeRaw("Station")
	val ingosStationClass = getRelativeRaw("IngosStation")
	val wdcggStationClass = getRelativeRaw("WdcggStation")
	val fluxnetStationClass = getRelativeRaw("FluxnetStation")
	val atmoDroughtStationClass = getRelativeRaw("AtmoStation")
	val sailDroneStationClass = getRelativeRaw("SailDrone")
	val neonStationClass = getRelativeRaw("NeonStation")

	val atmoStationClass = getRelativeRaw("AS")
	val ecoStationClass = getRelativeRaw("ES")
	val oceStationClass = getRelativeRaw("OS")
//	val tcClass = getRelativeRaw("ThematicCenter")
//	val cfClass = getRelativeRaw("CentralFacility")
	val orgClass = getRelativeRaw("Organization")
	val membershipClass = getRelativeRaw("Membership")
	val instrumentClass = getRelativeRaw("Instrument")

	val aquisitionClass = getRelativeRaw("DataAcquisition")
	val productionClass = getRelativeRaw("DataProduction")
	val submissionClass = getRelativeRaw("DataSubmission")
	val dataObjectClass = getRelativeRaw("DataObject")
	val docObjectClass = getRelativeRaw("DocumentObject")
	val dataObjectSpecClass = getRelativeRaw("DataObjectSpec")
	val collectionClass = getRelativeRaw("Collection")
	val spatialCoverageClass = getRelativeRaw("SpatialCoverage")
	val latLonBoxClass = getRelativeRaw("LatLonBox")
	val positionClass = getRelativeRaw("Position")
	val variableInfoClass = getRelativeRaw("VariableInfo")

	val hasElevation = getRelativeRaw("hasElevation")
	val hasLatitude = getRelativeRaw("hasLatitude")
	val hasNothernBound = getRelativeRaw("hasNothernBound")
	val hasSouthernBound = getRelativeRaw("hasSouthernBound")
	val hasLongitude = getRelativeRaw("hasLongitude")
	val hasEasternBound = getRelativeRaw("hasEasternBound")
	val hasWesternBound = getRelativeRaw("hasWesternBound")
	val hasSamplingPoint = getRelativeRaw("hasSamplingPoint")
	val hasSamplingHeight = getRelativeRaw("hasSamplingHeight")
	val hasName = getRelativeRaw("hasName")
	val hasStationId = getRelativeRaw("hasStationId")
	val hasStationClass = getRelativeRaw("hasStationClass")
	val hasLabelingDate = getRelativeRaw("hasLabelingDate")
	val hasResponsibleOrganization = getRelativeRaw("hasResponsibleOrganization")
	val countryCode = getRelativeRaw("countryCode")
	val country = getRelativeRaw("country")

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
	val hasEncoding = getRelativeRaw("hasEncoding")
	val hasFormatSpecificMeta = getRelativeRaw("hasFormatSpecificMetadata")
	val hasNumberOfRows = getRelativeRaw("hasNumberOfRows")
	val hasActualColumnNames = getRelativeRaw("hasActualColumnNames")
	val hasActualVariable = getRelativeRaw("hasActualVariable")
	val hasColumn = getRelativeRaw("hasColumn")
	val hasColumnTitle = getRelativeRaw("hasColumnTitle")
	val hasVariable = getRelativeRaw("hasVariable")
	val hasVariableTitle = getRelativeRaw("hasVariableTitle")
	val hasVariableName = getRelativeRaw("hasVariableName")
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
	val hasUnit = getRelativeRaw("hasUnit")
	val hasQuantityKind = getRelativeRaw("hasQuantityKind")
	val hasMinValue = getRelativeRaw("hasMinValue")
	val hasMaxValue = getRelativeRaw("hasMaxValue")
	val isOptionalVariable = getRelativeRaw("isOptionalVariable")
	val isOptionalColumn = getRelativeRaw("isOptionalColumn")
	val isRegexVariable = getRelativeRaw("isRegexVariable")
	val isRegexColumn = getRelativeRaw("isRegexColumn")

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

	val ancillaryValueClass = getRelativeRaw("AncillaryValue")
	val ancillaryEntryClass = getRelativeRaw("AncillaryEntry")

	val hasAncillaryDataValue = getRelativeRaw("hasAncillaryDataValue")
	val hasAncillaryObjectValue = getRelativeRaw("hasAncillaryObjectValue")
	val hasAncillaryEntry = getRelativeRaw("hasAncillaryEntry")

	val wdcggFormat = getRelativeRaw("asciiWdcggTimeSer")
//	val etcFormat = getRelativeRaw("asciiEtcTimeSer")
	val atcFormat = getRelativeRaw("asciiAtcTimeSer")
	val atcProductFormat = getRelativeRaw("asciiAtcProductTimeSer")
//	val socatFormat = getRelativeRaw("asciiOtcSocatTimeSer")

	object prov extends CustomVocab {
		val factory = top.factory
		implicit val bup = makeUriProvider(CpmetaVocab.ProvPrefix)

		val wasAssociatedWith = getRelativeRaw("wasAssociatedWith")
		val hadPrimarySource = getRelativeRaw("hadPrimarySource")
		val startedAtTime = getRelativeRaw("startedAtTime")
		val endedAtTime = getRelativeRaw("endedAtTime")
	}

	object dcterms extends CustomVocab {
		val factory = top.factory
		implicit val bup = makeUriProvider(CpmetaVocab.DctermsPrefix)

		val date = getRelativeRaw("date")
		val title = getRelativeRaw("title")
		val description = getRelativeRaw("description")
		val creator = getRelativeRaw("creator")
		val hasPart = getRelativeRaw("hasPart")
		val dateSubmitted = getRelativeRaw("dateSubmitted")
	}

	object sites extends CustomVocab {
		val factory = top.factory
		implicit val bup = makeUriProvider("https://meta.fieldsites.se/ontologies/sites/")
		val stationClass = getRelativeRaw("Station")
	}
}

object CpmetaVocab{
	val MetaPrefix = "http://meta.icos-cp.eu/ontologies/cpmeta/"
	val ProvPrefix = "http://www.w3.org/ns/prov#"
	val DctermsPrefix = "http://purl.org/dc/terms/"
}
