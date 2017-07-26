package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class CpmetaVocab (val factory: ValueFactory) extends CustomVocab { top =>

	val baseUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	val stationClass = getRelative("Station")
	val atmoStationClass = getRelative("AS")
	val ecoStationClass = getRelative("ES")
	val oceStationClass = getRelative("OS")
	val tcClass = getRelative("ThematicCenter")
	val cfClass = getRelative("CentralFacility")
	val orgClass = getRelative("Organization")
	val membershipClass = getRelative("Membership")

	val aquisitionClass = getRelative("DataAcquisition")
	val productionClass = getRelative("DataProduction")
	val submissionClass = getRelative("DataSubmission")
	val dataObjectClass = getRelative("DataObject")
	val latLonBoxClass = getRelative("LatLonBox")

	val hasLatitude = getRelative("hasLatitude")
	val hasNothernBound = getRelative("hasNothernBound")
	val hasSouthernBound = getRelative("hasSouthernBound")
	val hasLongitude = getRelative("hasLongitude")
	val hasEasternBound = getRelative("hasEasternBound")
	val hasWesternBound = getRelative("hasWesternBound")
	val hasName = getRelative("hasName")
	val hasStationId = getRelative("hasStationId")
	val country = getRelative("country")

	val hasSha256sum = getRelative("hasSha256sum")
	val wasAcquiredBy = getRelative("wasAcquiredBy")
	val wasSubmittedBy = getRelative("wasSubmittedBy")
	val wasProducedBy = getRelative("wasProducedBy")
	val wasPerformedBy = getRelative("wasPerformedBy")
	val wasPerformedWith = getRelative("wasPerformedWith")
	val wasParticipatedInBy = getRelative("wasParticipatedInBy")
	val wasHostedBy = getRelative("wasHostedBy")
	val hasDataLevel = getRelative("hasDataLevel")
	val hasObjectSpec = getRelative("hasObjectSpec")
	val hasFormat = getRelative("hasFormat")
	val hasEncoding = getRelative("hasEncoding")
	val hasFormatSpecificMeta = getRelative("hasFormatSpecificMetadata")
	val hasNumberOfRows = getRelative("hasNumberOfRows")
	val hasTemporalResolution = getRelative("hasTemporalResolution")
	val hasSpatialCoverage = getRelative("hasSpatialCoverage")

	val personClass = getRelative("Person")
	val roleClass = getRelative("Role")

	val hasFirstName = getRelative("hasFirstName")
	val hasLastName = getRelative("hasLastName")
	val hasEmail = getRelative("hasEmail")
	val hasRole = getRelative("hasRole")
	val hasMembership = getRelative("hasMembership")
	val atOrganization = getRelative("atOrganization")
	val hasStartTime = getRelative("hasStartTime")
	val hasEndTime = getRelative("hasEndTime")

	val ancillaryValueClass = getRelative("AncillaryValue")
	val ancillaryEntryClass = getRelative("AncillaryEntry")

	val hasAncillaryDataValue = getRelative("hasAncillaryDataValue")
	val hasAncillaryObjectValue = getRelative("hasAncillaryObjectValue")
	val hasAncillaryEntry = getRelative("hasAncillaryEntry")

	val wdcggFormat = getRelative("asciiWdcggTimeSer")
	val etcFormat = getRelative("asciiEtcTimeSer")
	val atcFormat = getRelative("asciiAtcTimeSer")
	val socatFormat = getRelative("asciiOtcSocatTimeSer")

	object prov extends CustomVocab {
		val factory = top.factory
		val baseUri = "http://www.w3.org/ns/prov#"
		val wasAssociatedWith = getRelative("wasAssociatedWith")
		val startedAtTime = getRelative("startedAtTime")
		val endedAtTime = getRelative("endedAtTime")
	}

	object dcterms extends CustomVocab {
		val factory = top.factory
		val baseUri = "http://purl.org/dc/terms/"
		val date = getRelative("date")
		val title = getRelative("title")
		val description = getRelative("description")
		val dateSubmitted = getRelative("dateSubmitted")
	}

}
