package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


class StationsVocab(val factory: ValueFactory) extends CustomVocab{
	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/ontologies/stationentry/")

	val station = getRelativeRaw("Station")
	val atmoStationClass = getRelativeRaw("AS")

	val hasShortName = getRelativeRaw("hasShortName")
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

	def getProperty(fieldName: String) = getRelativeRaw(fieldName)

	val files = new FilesVocab(factory)
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/files/")

	val hasType = getRelativeRaw("hasType")
	val hasName = getRelativeRaw("hasName")

	def getUri(hashsum: Sha256Sum) = getRelativeRaw(hashsum.id)
	def getFileHash(fileUri: IRI): Sha256Sum = Sha256Sum.fromString(fileUri.getLocalName).get
}
