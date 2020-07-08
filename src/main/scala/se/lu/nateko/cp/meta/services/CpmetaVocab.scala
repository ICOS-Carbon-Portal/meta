package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	implicit val bup = makeUriProvider(CpmetaVocab.MetaPrefix)

//	val stationClass = getRelative("Station")
	val ingosStationClass = getRelative("IngosStation")
	val wdcggStationClass = getRelative("WdcggStation")
	val fluxnetStationClass = getRelative("FluxnetStation")
	val atmoDroughtStationClass = getRelative("AtmoStation")
	val sailDroneStationClass = getRelative("SailDrone")
	val neonStationClass = getRelative("NeonStation")

	val atmoStationClass = getRelative("AS")
	val ecoStationClass = getRelative("ES")
	val oceStationClass = getRelative("OS")
//	val tcClass = getRelative("ThematicCenter")
//	val cfClass = getRelative("CentralFacility")
	val orgClass = getRelative("Organization")
	val membershipClass = getRelative("Membership")
	val instrumentClass = getRelative("Instrument")

	val aquisitionClass = getRelative("DataAcquisition")
	val productionClass = getRelative("DataProduction")
	val submissionClass = getRelative("DataSubmission")
	val dataObjectClass = getRelative("DataObject")
	val docObjectClass = getRelative("DocumentObject")
	val dataObjectSpecClass = getRelative("DataObjectSpec")
	val collectionClass = getRelative("Collection")
	val spatialCoverageClass = getRelative("SpatialCoverage")
	val latLonBoxClass = getRelative("LatLonBox")
	val positionClass = getRelative("Position")
	val variableInfoClass = getRelative("VariableInfo")

	val hasElevation = getRelative("hasElevation")
	val hasLatitude = getRelative("hasLatitude")
	val hasNothernBound = getRelative("hasNothernBound")
	val hasSouthernBound = getRelative("hasSouthernBound")
	val hasLongitude = getRelative("hasLongitude")
	val hasEasternBound = getRelative("hasEasternBound")
	val hasWesternBound = getRelative("hasWesternBound")
	val hasSamplingPoint = getRelative("hasSamplingPoint")
	val hasSamplingHeight = getRelative("hasSamplingHeight")
	val hasName = getRelative("hasName")
	val hasStationId = getRelative("hasStationId")
	val hasResponsibleOrganization = getRelative("hasResponsibleOrganization")
	val countryCode = getRelative("countryCode")
	val country = getRelative("country")

	val hasSha256sum = getRelative("hasSha256sum")
	val hasDoi = getRelative("hasDoi")
	val isNextVersionOf = getRelative("isNextVersionOf")
	val wasAcquiredBy = getRelative("wasAcquiredBy")
	val wasSubmittedBy = getRelative("wasSubmittedBy")
	val wasProducedBy = getRelative("wasProducedBy")
	val wasPerformedBy = getRelative("wasPerformedBy")
	val wasPerformedWith = getRelative("wasPerformedWith")
	val wasParticipatedInBy = getRelative("wasParticipatedInBy")
	val wasHostedBy = getRelative("wasHostedBy")
	val hasDataLevel = getRelative("hasDataLevel")
	val hasDataTheme = getRelative("hasDataTheme")
	val hasAssociatedProject = getRelative("hasAssociatedProject")
	val hasDocumentationObject = getRelative("hasDocumentationObject")
	val containsDataset = getRelative("containsDataset")
	val hasObjectSpec = getRelative("hasObjectSpec")
	val hasFormat = getRelative("hasFormat")
	val hasEncoding = getRelative("hasEncoding")
	val hasFormatSpecificMeta = getRelative("hasFormatSpecificMetadata")
	val hasNumberOfRows = getRelative("hasNumberOfRows")
	val hasActualColumnNames = getRelative("hasActualColumnNames")
	val hasActualVariable = getRelative("hasActualVariable")
	val hasColumn = getRelative("hasColumn")
	val hasColumnTitle = getRelative("hasColumnTitle")
	val hasVariable = getRelative("hasVariable")
	val hasVariableTitle = getRelative("hasVariableTitle")
	val hasVariableName = getRelative("hasVariableName")
	val hasKeywords = getRelative("hasKeywords")
	val hasKeyword = getRelative("hasKeyword")
	val hasSizeInBytes = getRelative("hasSizeInBytes")
	val hasTemporalResolution = getRelative("hasTemporalResolution")
	val hasSpatialCoverage = getRelative("hasSpatialCoverage")
	val asGeoJSON = getRelative("asGeoJSON")
	val hasCitationString = getRelative("hasCitationString")
	val wasPerformedAt = getRelative("wasPerformedAt")
	val hasEcosystemType = getRelative("hasEcosystemType")
	val hasClimateZone = getRelative("hasClimateZone")
	val hasMeanAnnualTemp = getRelative("hasMeanAnnualTemp")
	val hasOperationalPeriod = getRelative("hasOperationalPeriod")
	val operatesOn = getRelative("operatesOn")
	val hasDepiction = getRelative("hasDepiction")
	val hasValueType = getRelative("hasValueType")
	val hasUnit = getRelative("hasUnit")
	val hasQuantityKind = getRelative("hasQuantityKind")
	val hasMinValue = getRelative("hasMinValue")
	val hasMaxValue = getRelative("hasMaxValue")
	val isOptionalVariable = getRelative("isOptionalVariable")
	val isOptionalColumn = getRelative("isOptionalColumn")
	val isRegexVariable = getRelative("isRegexVariable")
	val isRegexColumn = getRelative("isRegexColumn")

	val personClass = getRelative("Person")
	val roleClass = getRelative("Role")

	val hasFirstName = getRelative("hasFirstName")
	val hasLastName = getRelative("hasLastName")
	val hasEmail = getRelative("hasEmail")
	val hasRole = getRelative("hasRole")
	val hasMembership = getRelative("hasMembership")
	val atOrganization = getRelative("atOrganization")
	val hasAttributionWeight = getRelative("hasAttributionWeight")
	val hasStartTime = getRelative("hasStartTime")
	val hasEndTime = getRelative("hasEndTime")
	val hasInstrumentOwner = getRelative("hasInstrumentOwner")
	val hasVendor = getRelative("hasVendor")
	val hasModel = getRelative("hasModel")
	val hasSerialNumber = getRelative("hasSerialNumber")
	val hasIcon = getRelative("hasIcon")
	val hasMarkerIcon = getRelative("hasMarkerIcon")

	val hasAtcId = getRelative("hasAtcId")
	val hasEtcId = getRelative("hasEtcId")
	val hasOtcId = getRelative("hasOtcId")

	val ancillaryValueClass = getRelative("AncillaryValue")
	val ancillaryEntryClass = getRelative("AncillaryEntry")

	val hasAncillaryDataValue = getRelative("hasAncillaryDataValue")
	val hasAncillaryObjectValue = getRelative("hasAncillaryObjectValue")
	val hasAncillaryEntry = getRelative("hasAncillaryEntry")

	val wdcggFormat = getRelative("asciiWdcggTimeSer")
//	val etcFormat = getRelative("asciiEtcTimeSer")
	val atcFormat = getRelative("asciiAtcTimeSer")
	val atcProductFormat = getRelative("asciiAtcProductTimeSer")
//	val socatFormat = getRelative("asciiOtcSocatTimeSer")

	object prov extends CustomVocab {
		val factory = top.factory
		implicit val bup = makeUriProvider(CpmetaVocab.ProvPrefix)

		val wasAssociatedWith = getRelative("wasAssociatedWith")
		val hadPrimarySource = getRelative("hadPrimarySource")
		val startedAtTime = getRelative("startedAtTime")
		val endedAtTime = getRelative("endedAtTime")
	}

	object dcterms extends CustomVocab {
		val factory = top.factory
		implicit val bup = makeUriProvider(CpmetaVocab.DctermsPrefix)

		val date = getRelative("date")
		val title = getRelative("title")
		val description = getRelative("description")
		val creator = getRelative("creator")
		val hasPart = getRelative("hasPart")
		val dateSubmitted = getRelative("dateSubmitted")
	}

//	object sites extends CustomVocab {
//		val factory = top.factory
//		implicit val bup = makeUriProvider("https://meta.fieldsites.se/ontologies/sites/")
//		val simpleSitesCsv = getRelative("simpleSitesCsv")
//	}
}

object CpmetaVocab{
	val MetaPrefix = "http://meta.icos-cp.eu/ontologies/cpmeta/"
	val ProvPrefix = "http://www.w3.org/ns/prov#"
	val DctermsPrefix = "http://purl.org/dc/terms/"
}
