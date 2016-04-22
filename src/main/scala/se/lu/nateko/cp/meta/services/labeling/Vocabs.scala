package se.lu.nateko.cp.meta.services.labeling

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import java.net.URLEncoder
import org.openrdf.model.URI


class StationsVocab(val factory: ValueFactory) extends CustomVocab{
	val baseUri = "http://meta.icos-cp.eu/ontologies/stationentry/"

	val station = getRelative("Station")

	val hasShortName = getRelative("hasShortName")
	val hasLongName = getRelative("hasLongName")
	val hasCountry = getRelative("hasCountry")
	val hasStationClass = getRelative("hasStationClass")
	val hasSiteType = getRelative("hasSiteType")
	val hasStationKind = getRelative("hasStationKind")
	val hasPiName = getRelative("hasPiName")
	val hasPiEmail = getRelative("hasPiEmail")
	val hasPreIcosMeasurements = getRelative("hasPreIcosMeasurements")
	val isAlreadyOperational = getRelative("isAlreadyOperational")
	val hasFundingForConstruction = getRelative("hasFundingForConstruction")
	val hasFundingForOperation = getRelative("hasFundingForOperation")
	val hasElevationAboveSea = getRelative("hasElevationAboveSea")
	val hasElevationAboveGround = getRelative("hasElevationAboveGround")
	val hasOperationalDateEstimate = getRelative("hasOperationalDateEstimate")
	val hasLocationDescription = getRelative("hasLocationDescription")
	val hasLat = getRelative("hasLat")
	val hasLon = getRelative("hasLon")
}

class StationStructuringVocab(factory: ValueFactory) extends StationsVocab(factory){
	val hasPi = getRelative("hasPi")
	val hasFirstName = getRelative("hasFirstName")
	val hasLastName = getRelative("hasLastName")
	val hasEmail = getRelative("hasEmail")
	val hasAffiliation = getRelative("hasAffiliation")
	val hasPhone = getRelative("hasPhone")
	val hasAssociatedFile = getRelative("hasAssociatedFile")
	val hasApplicationStatus = getRelative("hasApplicationStatus")
	val PI = getRelative("PI")

	val files = new FilesVocab(factory)

	def piUri(email: String) = getRelativeRaw("PI/" + URLEncoder.encode(email, "UTF-8"))
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	val baseUri = "http://meta.icos-cp.eu/files/"

	val hasType = getRelative("hasType")
	val hasName = getRelative("hasName")

	def getUri(hashsum: String) = getRelative(hashsum)
	def getFileHash(fileUri: URI) = fileUri.getLocalName
}
