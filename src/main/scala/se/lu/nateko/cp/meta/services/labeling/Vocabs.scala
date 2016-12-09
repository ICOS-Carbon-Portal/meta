package se.lu.nateko.cp.meta.services.labeling

import org.openrdf.model.URI
import org.openrdf.model.ValueFactory

import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


class StationsVocab(val factory: ValueFactory) extends CustomVocab{
	val baseUri = "http://meta.icos-cp.eu/ontologies/stationentry/"

	val station = getRelative("Station")
	val atmoStationClass = getRelative("AS")

	val hasShortName = getRelative("hasShortName")
	val hasPi = getRelative("hasPi")
	val hasFirstName = getRelative("hasFirstName")
	val hasLastName = getRelative("hasLastName")
	val hasEmail = getRelative("hasEmail")
	val hasAffiliation = getRelative("hasAffiliation")
	val hasPhone = getRelative("hasPhone")
	val hasAssociatedFile = getRelative("hasAssociatedFile")
	val hasApplicationStatus = getRelative("hasApplicationStatus")

	val files = new FilesVocab(factory)
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	val baseUri = "http://meta.icos-cp.eu/files/"

	val hasType = getRelative("hasType")
	val hasName = getRelative("hasName")

	def getUri(hashsum: Sha256Sum) = getRelative(hashsum.id)
	def getFileHash(fileUri: URI): Sha256Sum = Sha256Sum.fromString(fileUri.getLocalName).get
}
