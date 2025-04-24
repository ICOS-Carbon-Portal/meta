package se.lu.nateko.cp.meta.services.labeling

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


class StationsVocab(val factory: ValueFactory) extends CustomVocab{
	private given BaseUriProvider = makeUriProvider("http://meta.icos-cp.eu/ontologies/stationentry/")

	val station = getRelativeRaw("Station")
	val atmoStationClass = getRelativeRaw("AS")
	val ecoStationClass = getRelativeRaw("ES")
	val oceStationClass = getRelativeRaw("OS")

	val hasShortName = getRelativeRaw("hasShortName")
	val hasLongName = getRelativeRaw("hasLongName")
	val hasPi = getRelativeRaw("hasPi")
	val hasDeputyPi = getRelativeRaw("hasDeputyPi")
	val hasFirstName = getRelativeRaw("hasFirstName")
	val hasLastName = getRelativeRaw("hasLastName")
	val hasEmail = getRelativeRaw("hasEmail")
	val hasAffiliation = getRelativeRaw("hasAffiliation")
	val hasPhone = getRelativeRaw("hasPhone")
	val hasAssociatedFile = getRelativeRaw("hasAssociatedFile")
	val hasApplicationStatus = getRelativeRaw("hasApplicationStatus")
	val hasAppStatusComment = getRelativeRaw("hasAppStatusComment")
	val hasAppStatusDate = getRelativeRaw("hasAppStatusDate")
	val hasProductionCounterpart = getRelativeRaw("hasProductionCounterpart")
	val hasStationClass = getRelativeRaw("hasStationClass")

	val labelingEndDate = getRelativeRaw("labelingEndDate")
	val labelingJoinYear = getRelativeRaw("labelingJoinYear")
	val step2EndDate = getRelativeRaw("step2EndDate")
	val step2StartDate = getRelativeRaw("step2StartDate")
	val step1EndDate = getRelativeRaw("step1EndDate")
	val step1StartDate = getRelativeRaw("step1StartDate")

	def getProperty(fieldName: String) = getRelativeRaw(fieldName)

	val files = new FilesVocab(factory)
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	private given BaseUriProvider = makeUriProvider("http://meta.icos-cp.eu/files/")

	val hasType = getRelativeRaw("hasType")
	val hasName = getRelativeRaw("hasName")

	def getUri(hashsum: Sha256Sum) = getRelativeRaw(hashsum.id)
	def getFileHash(fileUri: IRI): Sha256Sum = Sha256Sum.fromString(fileUri.getLocalName).get
}
