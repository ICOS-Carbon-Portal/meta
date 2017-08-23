package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.{URI => JavaUri}
import se.lu.nateko.cp.meta.core.etcupload.{StationId => EtcStationId}
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.ConfigLoader

class CpVocab (val factory: ValueFactory) extends CustomVocab {

	private val config: MetaCoreConfig = ConfigLoader.core
	val baseUri = config.metaResourcePrefix.toString

	def getAtmosphericStation(siteId: String) = getRelative("stations/AS_", siteId)
	def getEcosystemStation(id: EtcStationId) = getRelative("stations/ES_", id.id)
	def getOceanStation(siteId: String) = getRelative("stations/OS_", siteId)

	def getPerson(firstName: String, lastName: String) = getRelativeRaw(
		s"people/${urlEncode(firstName)}_${urlEncode(lastName)}"
	)

	def getEtcMembership(id: EtcStationId, roleId: String, lastName: String) = getRelative(
		"memberships/", s"ES_${id.id}_${roleId}_$lastName"
	)

	def getMembership(orgId: String, roleId: String, lastName: String) = getRelative(
		"memberships/", s"${orgId}_${roleId}_$lastName"
	)

	def getRole(roleId: String) = getRelative("roles/", roleId)

	def getOrganization(orgId: String) = getRelative("organizations/", orgId)

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(getOrganization)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)

	def getDataObject(hash: Sha256Sum) = factory.createIRI(config.landingPagePrefix.toString, hash.id)

	def getDataObjectAccessUrl(hash: Sha256Sum, fileName: String): JavaUri =
		new JavaUri(s"${config.dataObjPrefix + hash.id}/${urlEncode(fileName)}")

	def getAcquisition(hash: Sha256Sum) = getRelative("acq_" + hash.id)
	def getProduction(hash: Sha256Sum) = getRelative("prod_" + hash.id)
	def getSubmission(hash: Sha256Sum) = getRelative("subm_" + hash.id)
	def getSpatialCoverate(hash: Sha256Sum) = getRelative("spcov_" + hash.id)

	def getObjectSpecification(lastSegment: String) = getRelative("cpmeta/", lastSegment)
}
