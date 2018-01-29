package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum


class StationsVocab(val factory: ValueFactory) extends CustomVocab{
	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/ontologies/stationentry/")

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

	def getProperty(fieldName: String) = getRelative(fieldName)

	val files = new FilesVocab(factory)
}


class FilesVocab(val factory: ValueFactory) extends CustomVocab{
	implicit val bup = makeUriProvider("http://meta.icos-cp.eu/files/")

	val hasType = getRelative("hasType")
	val hasName = getRelative("hasName")

	def getUri(hashsum: Sha256Sum) = getRelative(hashsum.id)
	def getFileHash(fileUri: IRI): Sha256Sum = Sha256Sum.fromString(fileUri.getLocalName).get
}
