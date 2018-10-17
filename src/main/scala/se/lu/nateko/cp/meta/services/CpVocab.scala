package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI

import se.lu.nateko.cp.meta.core.etcupload.{StationId => EtcStationId}
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import se.lu.nateko.cp.meta.ConfigLoader

class CpVocab (val factory: ValueFactory)(implicit envriConfigs: EnvriConfigs) extends CustomVocab {
	import CpVocab._

	def getConfig(implicit envri: Envri) = envriConfigs(envri)

	private implicit def baseUriProviderForEnvri(implicit envri: Envri) =
		makeUriProvider(getConfig(envri).metaResourcePrefix.toString)

	val icosBup = baseUriProviderForEnvri(Envri.ICOS)

	def getTcStation(tcId: String, stationId: String) = getRelative(s"stations/${tcId}_", stationId)(icosBup)
	def getAtmosphericStation(stationId: String) = getTcStation("AS", stationId)
	def getEcosystemStation(id: EtcStationId) = getTcStation("ES", id.id)
	def getOceanStation(stationId: String) = getTcStation("OS", stationId)

	def getPerson(firstName: String, lastName: String)(implicit envri: Envri) = getRelativeRaw(
		s"people/${urlEncode(firstName)}_${urlEncode(lastName)}"
	)

	def getEtcMembership(station: EtcStationId, roleId: String, lastName: String) = getRelative(
		"memberships/", s"ES_${station.id}_${roleId}_$lastName"
	)(icosBup)

	def getMembership(orgId: String, roleId: String, lastName: String)(implicit envri: Envri) = getRelative(
		"memberships/", s"${orgId}_${roleId}_$lastName"
	)

	def getRole(roleId: String)(implicit envri: Envri) = getRelative("roles/", roleId)

	def getOrganization(orgId: String)(implicit envri: Envri) = getRelative("organizations/", orgId)

	def getEtcInstrument(station: EtcStationId, id: Int) = getRelative(
		"instruments/", s"ETC_${station.id}_$id"
	)(icosBup)

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(getOrganization(_)(Envri.ICOS))

	val icosProject = getRelative("projects/", "icos")(icosBup)
	val atmoTheme = getRelative("themes/", "atmosphere")(icosBup)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)(icosBup)

	def getDataObject(hash: Sha256Sum)(implicit envri: Envri) = factory.createIRI(s"${getConfig.metaPrefix}objects/", hash.id)
	def getCollection(hash: Sha256Sum)(implicit envri: Envri) = factory.createIRI(s"${getConfig.metaPrefix}collections/", hash.id)

	def getDataObjectAccessUrl(hash: Sha256Sum)(implicit envri: Envri) = new URI(s"${getConfig.dataPrefix}objects/${hash.id}")

	def getAcquisition(hash: Sha256Sum)(implicit envri: Envri) = getRelative(AcqPrefix + hash.id)
	def getProduction(hash: Sha256Sum)(implicit envri: Envri) = getRelative(ProdPrefix + hash.id)
	def getSubmission(hash: Sha256Sum)(implicit envri: Envri) = getRelative(SubmPrefix + hash.id)
	def getSpatialCoverage(hash: Sha256Sum)(implicit envri: Envri) = getRelative(SpatCovPrefix + hash.id)

	def getObjectSpecification(lastSegment: String)(implicit envri: Envri) =
		if(envri == Envri.ICOS) getRelative("cpmeta/", lastSegment)
		else getRelative("objspecs/", lastSegment)
}

object CpVocab{
	val AcqPrefix = "acq_"
	val ProdPrefix = "prod_"
	val SubmPrefix = "subm_"
	val SpatCovPrefix = "spcov_"

	object Acquisition{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, AcqPrefix)
	}

	object Submission{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, SubmPrefix)
	}

	object DataObject{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, "")
	}

	def isIngosArchive(objSpec: IRI): Boolean = objSpec.getLocalName == "ingosArchive"

	private def asPrefWithHash(iri: IRI, prefix: String): Option[Sha256Sum] = {
		val segm = iri.getLocalName
		if(segm.startsWith(prefix))
			Sha256Sum.fromBase64Url(segm.stripPrefix(prefix)).toOption
		else
			None
	}
}
