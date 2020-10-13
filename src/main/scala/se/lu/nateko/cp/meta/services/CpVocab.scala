package se.lu.nateko.cp.meta.services

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.api.{CustomVocab, UriId}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.{objectPrefix, collectionPrefix, objectPathPrefix}
import se.lu.nateko.cp.meta.core.data.{staticObjLandingPage, staticObjAccessUrl, staticCollLandingPage}
import se.lu.nateko.cp.meta.core.data.Envri.{ Envri, EnvriConfigs }
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.etcupload.{ StationId => EtcStationId }
import se.lu.nateko.cp.meta.icos.ETC
import se.lu.nateko.cp.meta.icos.TcConf
import se.lu.nateko.cp.meta.icos.Role

class CpVocab (val factory: ValueFactory)(implicit envriConfigs: EnvriConfigs) extends CustomVocab {
	import CpVocab._
	import CustomVocab.urlEncode

	private val baseResourceUriProviders: Map[Envri, BaseUriProvider] = envriConfigs.map{
		case (envri, envriConf) =>
			envri -> makeUriProvider(s"${envriConf.metaItemPrefix}resources/")
	}

	private def getConfig(implicit envri: Envri) = envriConfigs(envri)

	private implicit def baseUriProviderForEnvri(implicit envri: Envri) = baseResourceUriProviders(envri)

	val icosBup = baseUriProviderForEnvri(Envri.ICOS)

	def getIcosLikeStation(stationId: UriId) = getRelative(s"stations/", stationId)(icosBup)
	def getEcosystemStation(id: EtcStationId) = getIcosLikeStation(TcConf.stationId[ETC.type](id.id))

	def getPerson(firstName: String, lastName: String)(implicit envri: Envri): IRI = getPerson(getPersonCpId(firstName, lastName))
	def getPerson(cpId: UriId)(implicit envri: Envri): IRI = getRelativeRaw("people/" + cpId)

//	def getEtcMembership(station: EtcStationId, roleId: String, lastName: String) = getRelative(
//		"memberships/", s"ES_${station.id}_${roleId}_$lastName"
//	)(icosBup)

	def getMembership(membId: UriId)(implicit envri: Envri): IRI = getRelative("memberships/", membId)
	def getMembership(orgId: UriId, role: Role, lastName: String)(implicit envri: Envri): IRI =
		getMembership(UriId(s"${orgId}_${role.name}_${UriId.escaped(lastName)}"))

	def getRole(role: Role)(implicit envri: Envri) = getRelative(RolesPrefix, UriId(role.name))

	def getOrganization(orgId: UriId)(implicit envri: Envri) = getRelative("organizations/", orgId)

	def getIcosInstrument(id: UriId) = getRelative("instruments/", id)(icosBup)
	def getEtcInstrument(station: EtcStationId, id: Int) = getIcosInstrument(getEtcInstrId(station, id))

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(UriId.apply).map(getOrganization(_)(Envri.ICOS))

	val icosProject = getRelativeRaw("projects/icos")(icosBup)
	val atmoTheme = getRelativeRaw("themes/atmosphere")(icosBup)

	def getAncillaryEntry(valueId: String) = getRelativeRaw("ancillary/" + valueId)(icosBup)

	def getStaticObject(hash: Sha256Sum)(implicit envri: Envri) = factory.createIRI(staticObjLandingPage(hash)(getConfig).toString)
	def getCollection(hash: Sha256Sum)(implicit envri: Envri) = factory.createIRI(staticCollLandingPage(hash)(getConfig).toString)

	def getStaticObjectAccessUrl(hash: Sha256Sum)(implicit envri: Envri) = staticObjAccessUrl(hash)(getConfig)

	def getAcquisition(hash: Sha256Sum)(implicit envri: Envri) = getRelativeRaw(AcqPrefix + hash.id)
	def getProduction(hash: Sha256Sum)(implicit envri: Envri) = getRelativeRaw(ProdPrefix + hash.id)
	def getSubmission(hash: Sha256Sum)(implicit envri: Envri) = getRelativeRaw(SubmPrefix + hash.id)
	def getSpatialCoverage(hash: Sha256Sum)(implicit envri: Envri) = getRelativeRaw(SpatCovPrefix + hash.id)
	def getVarInfo(hash: Sha256Sum, varLabel: String)(implicit envri: Envri) = getRelativeRaw(s"${VarInfoPrefix}${urlEncode(varLabel)}_${hash.id}")
	def getPosition(pos: Position)(implicit envri: Envri) = getRelativeRaw(s"position_${pos.lat6}_${pos.lon6}")

	def getObjectSpecification(lastSegment: UriId)(implicit envri: Envri) =
		if(envri == Envri.ICOS) getRelative("cpmeta/", lastSegment)
		else getRelative("objspecs/", lastSegment)
}

object CpVocab{
	import CustomVocab.urlEncode
	import Sha256Sum.IdLength

	val AcqPrefix = "acq_"
	val ProdPrefix = "prod_"
	val SubmPrefix = "subm_"
	val SpatCovPrefix = "spcov_"
	val VarInfoPrefix = "varinfo_"
	val RolesPrefix = "roles/"

	object Acquisition{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, AcqPrefix)
	}

	object Submission{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, SubmPrefix)
	}

	object SpatialCoverage{
		def unapply(uri: java.net.URI): Option[Sha256Sum] = asPrefWithHashSuff(uri.getPath.split('/').last, SpatCovPrefix)
	}

	object DataObject{
		def unapply(iri: IRI): Option[(Sha256Sum, String)] = asPrefWithHash(iri, "")
			.map(hash => hash -> iri.stringValue.stripSuffix(iri.getLocalName))
	}

	object IcosRole{
		def unapply(iri: IRI): Option[Role] = {
			val segms = iri.stringValue.split('/')
			if(segms.length < 2 || !RolesPrefix.startsWith(segms(segms.length - 2))) None else{
				Role.forName(segms.last)
			}
		}
	}

	object VarInfo{

		def unapply(iri: IRI): Option[(Sha256Sum, String)] = {
			val uriSegm = iri.getLocalName
			asPrefWithHashSuff(uriSegm, VarInfoPrefix).flatMap{hash =>
				if(uriSegm(uriSegm.length - IdLength - 1) == '_')
					Some(hash -> uriSegm.drop(VarInfoPrefix.length).dropRight(IdLength + 1))
				else None
			}
		}
	}

	def isIngosArchive(objSpec: IRI): Boolean = objSpec.getLocalName == "ingosArchive"

	def getEtcInstrId(station: EtcStationId, id: Int) = TcConf.tcScopedId[ETC.type](s"${station.id}_$id")

	private def asPrefWithHash(iri: IRI, prefix: String): Option[Sha256Sum] = {
		val uriSegm = iri.getLocalName
		if(uriSegm.length == prefix.length + IdLength) asPrefWithHashSuff(uriSegm, prefix) else None
	}

	private def asPrefWithHashSuff(uriSegm: String, prefix: String): Option[Sha256Sum] =
		if(uriSegm.startsWith(prefix))
			Sha256Sum.fromBase64Url(uriSegm.takeRight(IdLength)).toOption
		else
			None

	def getPersonCpId(firstName: String, lastName: String) = UriId(s"${urlEncode(firstName)}_${urlEncode(lastName)}")

}
