package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
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

	def getMembership(orgId: String, roleId: String, lastName: String) = getRelative(
		"memberships/", s"${orgId}_${roleId}_$lastName"
	)

	def getRole(roleId: String) = getRelative("roles/", roleId)

	def getOrganization(orgId: String) = getRelative("organizations/", orgId)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)

	def getDataObject(hash: Sha256Sum) = factory.createIRI("https://meta.icos-cp.eu/objects/", hash.id)

	def getDataObjectAccessUrl(hash: Sha256Sum, fileName: String): JavaUri =
		new JavaUri(s"https://data.icos-cp.eu/objects/${hash.id}/${urlEncode(fileName)}")

	def getAcquisition(hash: Sha256Sum) = getRelative("acq_" + hash.id)
	def getProduction(hash: Sha256Sum) = getRelative("prod_" + hash.id)
	def getSubmission(hash: Sha256Sum) = getRelative("subm_" + hash.id)
	def getSpatialCoverate(hash: Sha256Sum) = getRelative("spcov_" + hash.id)
}
