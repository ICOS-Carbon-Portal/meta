package se.lu.nateko.cp.meta.services

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.{URI => JavaUri}

class CpVocab (val factory: ValueFactory) extends CustomVocab {

	val baseUri = "http://meta.icos-cp.eu/resources/"

	def getAtmosphericStation(siteId: String) = getRelative("stations/AS_", siteId)
	def getEcosystemStation(siteId: String) = getRelative("stations/ES_", siteId)
	def getOceanStation(siteId: String) = getRelative("stations/OS_", siteId)

	def getPerson(firstName: String, lastName: String) = getRelativeRaw(
		s"people/${urlEncode(firstName)}_${urlEncode(lastName)}"
	)

	def getEtcMembership(siteId: String, roleId: String, lastName: String) = getRelative(
		"memberships/", s"ES_${siteId}_${roleId}_$lastName"
	)
	def getRole(roleId: String) = getRelative("roles/", roleId)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)

	def getDataObject(hash: Sha256Sum) = factory.createURI("https://meta.icos-cp.eu/objects/", hash.id)

	def getDataObjectAccessUrl(hash: Sha256Sum, fileName: Option[String]): JavaUri = {
		val filePath = fileName.map("/" + urlEncode(_)).getOrElse("")
		new JavaUri(s"https://data.icos-cp.eu/objects/${hash.id}$filePath")
	}

	def getAcquisition(hash: Sha256Sum) = getRelative("acq_" + hash.id)
	def getProduction(hash: Sha256Sum) = getRelative("prod_" + hash.id)
	def getSubmission(hash: Sha256Sum) = getRelative("subm_" + hash.id)
}
