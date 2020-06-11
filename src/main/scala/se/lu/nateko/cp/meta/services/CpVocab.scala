package se.lu.nateko.cp.meta.services

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.api.CustomVocab
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.{objectPrefix, collectionPrefix, objectPathPrefix}
import se.lu.nateko.cp.meta.core.data.{staticObjLandingPage, staticObjAccessUrl, staticCollLandingPage}
import se.lu.nateko.cp.meta.core.data.Envri.{ Envri, EnvriConfigs }
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.etcupload.{ StationId => EtcStationId }
import se.lu.nateko.cp.meta.icos.ETC
import se.lu.nateko.cp.meta.icos.TcConf

class CpVocab (val factory: ValueFactory)(implicit envriConfigs: EnvriConfigs) extends CustomVocab {
	import CpVocab._

	private val baseResourceUriProviders: Map[Envri, BaseUriProvider] = envriConfigs.map{
		case (envri, envriConf) =>
			envri -> makeUriProvider(s"${envriConf.metaItemPrefix}resources/")
	}

	private def getConfig(implicit envri: Envri) = envriConfigs(envri)

	private implicit def baseUriProviderForEnvri(implicit envri: Envri) = baseResourceUriProviders(envri)

	val icosBup = baseUriProviderForEnvri(Envri.ICOS)

	def getIcosLikeStation(stationId: String) = getRelative(s"stations/", stationId)(icosBup)
	def getEcosystemStation(id: EtcStationId) = getIcosLikeStation(TcConf.stationId[ETC.type](id.id))

	def getPerson(firstName: String, lastName: String)(implicit envri: Envri): IRI = getPerson(getPersonCpId(firstName, lastName))
	def getPerson(cpId: String)(implicit envri: Envri): IRI = getRelativeRaw("people/" + cpId)

//	def getEtcMembership(station: EtcStationId, roleId: String, lastName: String) = getRelative(
//		"memberships/", s"ES_${station.id}_${roleId}_$lastName"
//	)(icosBup)

	def getMembership(membId: String)(implicit envri: Envri): IRI = getRelative("memberships/", membId)
	def getMembership(orgId: String, roleId: String, lastName: String)(implicit envri: Envri): IRI =
		getMembership(s"${orgId}_${roleId}_$lastName")

	def getRole(roleId: String)(implicit envri: Envri) = getRelative("roles/", roleId)

	def getOrganization(orgId: String)(implicit envri: Envri) = getRelative("organizations/", orgId)
	def getOrganizationId(org: IRI): String = CustomVocab.decodedLocName(org)

	def getIcosInstrument(id: String) = getRelative("instruments/", id)(icosBup)
	def getEtcInstrument(station: EtcStationId, id: Int) = getIcosInstrument(getEtcInstrId(station, id))

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(getOrganization(_)(Envri.ICOS))

	val icosProject = getRelative("projects/", "icos")(icosBup)
	val atmoTheme = getRelative("themes/", "atmosphere")(icosBup)

	def getAncillaryEntry(valueId: String) = getRelative("ancillary/", valueId)(icosBup)

	def getStaticObject(hash: Sha256Sum)(implicit envri: Envri) = factory.createIRI(staticObjLandingPage(hash)(getConfig).toString)
	def getCollection(hash: Sha256Sum)(implicit envri: Envri) = factory.createIRI(staticCollLandingPage(hash)(getConfig).toString)

	def getStaticObjectAccessUrl(hash: Sha256Sum)(implicit envri: Envri) = staticObjAccessUrl(hash)(getConfig)

	def getAcquisition(hash: Sha256Sum)(implicit envri: Envri) = getRelative(AcqPrefix + hash.id)
	def getProduction(hash: Sha256Sum)(implicit envri: Envri) = getRelative(ProdPrefix + hash.id)
	def getSubmission(hash: Sha256Sum)(implicit envri: Envri) = getRelative(SubmPrefix + hash.id)
	def getSpatialCoverage(hash: Sha256Sum)(implicit envri: Envri) = getRelative(SpatCovPrefix + hash.id)
	def getVarInfo(hash: Sha256Sum, varLabel: String)(implicit envri: Envri) = getRelative(s"${VarInfoPrefix}_${varLabel}_${hash.id}")
	def getPosition(pos: Position)(implicit envri: Envri) = getRelative(s"position_${pos.lat6}_${pos.lon6}")

	def getObjectSpecification(lastSegment: String)(implicit envri: Envri) =
		if(envri == Envri.ICOS) getRelative("cpmeta/", lastSegment)
		else getRelative("objspecs/", lastSegment)
}

object CpVocab{
	import CustomVocab.urlEncode

	val AcqPrefix = "acq_"
	val ProdPrefix = "prod_"
	val SubmPrefix = "subm_"
	val SpatCovPrefix = "spcov_"
	val VarInfoPrefix = "varinfo_"

	object Acquisition{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, AcqPrefix)
	}

	object Submission{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, SubmPrefix)
	}

	object DataObject{
		def unapply(iri: IRI): Option[(Sha256Sum, String)] = asPrefWithHash(iri, "")
			.map(hash => hash -> iri.stringValue.stripSuffix(iri.getLocalName))
	}

	def isIngosArchive(objSpec: IRI): Boolean = objSpec.getLocalName == "ingosArchive"

	def getEtcInstrId(station: EtcStationId, id: Int) = TcConf.tcScopedId[ETC.type](s"${station.id}_$id")

	private def asPrefWithHash(iri: IRI, prefix: String): Option[Sha256Sum] = {
		val segm = iri.getLocalName
		if(segm.startsWith(prefix))
			Sha256Sum.fromBase64Url(segm.stripPrefix(prefix)).toOption
		else
			None
	}

	def getPersonCpId(firstName: String, lastName: String) = s"${urlEncode(firstName)}_${urlEncode(lastName)}"
}
