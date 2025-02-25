package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


class StationsVocab(val factory: ValueFactory) extends CustomVocab{
	private given BaseUriProvider = makeUriProvider("http://meta.icos-cp.eu/ontologies/stationentry/")

	val station: IRI = getRelativeRaw("Station")
	val atmoStationClass: IRI = getRelativeRaw("AS")
	val ecoStationClass: IRI = getRelativeRaw("ES")
	val oceStationClass: IRI = getRelativeRaw("OS")

	val hasShortName: IRI = getRelativeRaw("hasShortName")
	val hasLongName: IRI = getRelativeRaw("hasLongName")
	val hasPi: IRI = getRelativeRaw("hasPi")
	val hasDeputyPi: IRI = getRelativeRaw("hasDeputyPi")
	val hasFirstName: IRI = getRelativeRaw("hasFirstName")
	val hasLastName: IRI = getRelativeRaw("hasLastName")
	val hasEmail: IRI = getRelativeRaw("hasEmail")
	val hasAffiliation: IRI = getRelativeRaw("hasAffiliation")
	val hasPhone: IRI = getRelativeRaw("hasPhone")
	val hasAssociatedFile: IRI = getRelativeRaw("hasAssociatedFile")
	val hasApplicationStatus: IRI = getRelativeRaw("hasApplicationStatus")
	val hasAppStatusComment: IRI = getRelativeRaw("hasAppStatusComment")
	val hasAppStatusDate: IRI = getRelativeRaw("hasAppStatusDate")
	val hasProductionCounterpart: IRI = getRelativeRaw("hasProductionCounterpart")
	val hasStationClass: IRI = getRelativeRaw("hasStationClass")

	val labelingEndDate: IRI = getRelativeRaw("labelingEndDate")
	val labelingJoinYear: IRI = getRelativeRaw("labelingJoinYear")
	val step2EndDate: IRI = getRelativeRaw("step2EndDate")
	val step2StartDate: IRI = getRelativeRaw("step2StartDate")
	val step1EndDate: IRI = getRelativeRaw("step1EndDate")
	val step1StartDate: IRI = getRelativeRaw("step1StartDate")

	def getProperty(fieldName: String): IRI = getRelativeRaw(fieldName)

	val files = new FilesVocab(factory)
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	private given BaseUriProvider = makeUriProvider("http://meta.icos-cp.eu/files/")

	val hasType: IRI = getRelativeRaw("hasType")
	val hasName: IRI = getRelativeRaw("hasName")

	def getUri(hashsum: Sha256Sum): IRI = getRelativeRaw(hashsum.id)
	def getFileHash(fileUri: IRI): Sha256Sum = Sha256Sum.fromString(fileUri.getLocalName).get
}
