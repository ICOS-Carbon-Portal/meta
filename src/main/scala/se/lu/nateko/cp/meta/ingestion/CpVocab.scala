package se.lu.nateko.cp.meta.ingestion

import org.openrdf.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab

class CpVocab (val factory: ValueFactory) extends CustomVocab {

	val baseUri = "http://meta.icos-cp.eu/resources/"

	def getAtmosphericStation(siteId: String) = getRelative("stations/AS_", siteId)
	def getEcosystemStation(siteId: String) = getRelative("stations/ES_", siteId)
	def getOceanStation(siteId: String) = getRelative("stations/OS_", siteId)

	def getPerson(firstName: String, lastName: String) = getRelativeRaw(
		s"people/${urlEncode(firstName)}_${urlEncode(lastName)}"
	)

	def getEtcMembership(siteId: String, roleId: String, lastName: String) = getRelative(
		"membership/", s"ES_${siteId}_${roleId}_$lastName"
	)
	def getRole(roleId: String) = getRelative("roles/", roleId)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)
}
