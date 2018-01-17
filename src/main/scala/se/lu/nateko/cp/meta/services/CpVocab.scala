package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI
import se.lu.nateko.cp.meta.core.MetaCoreConfig.EnvriConfigs
import se.lu.nateko.cp.meta.core.etcupload.{StationId => EtcStationId}
import se.lu.nateko.cp.meta.core.EnvriConfig
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.ConfigLoader

class CpVocab (val factory: ValueFactory)(implicit envriConfigs: EnvriConfigs) extends CustomVocab {

	//TODO Generalize to SITES and ICOS
	private val config: EnvriConfig = envriConfigs(Envri.ICOS)
	val baseUri = config.metaResourcePrefix.toString

	def getAtmosphericStation(siteId: String) = getRelative("stations/AS_", siteId)
	def getEcosystemStation(id: EtcStationId) = getRelative("stations/ES_", id.id)
	def getOceanStation(siteId: String) = getRelative("stations/OS_", siteId)

	def getPerson(firstName: String, lastName: String) = getRelativeRaw(
		s"people/${urlEncode(firstName)}_${urlEncode(lastName)}"
	)

	def getEtcMembership(station: EtcStationId, roleId: String, lastName: String) = getRelative(
		"memberships/", s"ES_${station.id}_${roleId}_$lastName"
	)

	def getMembership(orgId: String, roleId: String, lastName: String) = getRelative(
		"memberships/", s"${orgId}_${roleId}_$lastName"
	)

	def getRole(roleId: String) = getRelative("roles/", roleId)

	def getOrganization(orgId: String) = getRelative("organizations/", orgId)

	def getEtcInstrument(station: EtcStationId, id: Int) = getRelative(
		"instruments/", s"ETC_${station.id}_$id"
	)

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(getOrganization)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)

	def getDataObject(hash: Sha256Sum) = factory.createIRI(s"${config.metaPrefix}objects/", hash.id)
	def getCollection(hash: Sha256Sum) = factory.createIRI(s"${config.metaPrefix}collections/", hash.id)

	def getDataObjectAccessUrl(hash: Sha256Sum) = new URI(s"${config.dataPrefix}objects/${hash.id}")

	def getAcquisition(hash: Sha256Sum) = getRelative("acq_" + hash.id)
	def getProduction(hash: Sha256Sum) = getRelative("prod_" + hash.id)
	def getSubmission(hash: Sha256Sum) = getRelative("subm_" + hash.id)
	def getSpatialCoverate(hash: Sha256Sum) = getRelative("spcov_" + hash.id)

	def getObjectSpecification(lastSegment: String) = getRelative("cpmeta/", lastSegment)
}

object CpVocab{

	def inferEnvri(dobj: URI)(implicit configs: EnvriConfigs): Envri.Value = configs
		.collectFirst{
			case (envri, conf) if dobj.getHost == conf.metaPrefix.getHost => envri
		}
		.getOrElse(Envri.ICOS)

	def inferEnvri(hostname: String)(implicit configs: EnvriConfigs): Envri.Value =
		inferEnvri(new URI(null, hostname, null, null))
}
