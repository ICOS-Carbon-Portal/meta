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
import se.lu.nateko.cp.meta.icos.{TC, TcId, ETC, TcConf, Role}

class CpVocab (val factory: ValueFactory)(using envriConfigs: EnvriConfigs) extends CustomVocab {
	import CpVocab._
	import CustomVocab.urlEncode

	private val baseResourceUriProviders: Map[Envri, BaseUriProvider] = envriConfigs.map{
		case (envri, envriConf) =>
			envri -> makeUriProvider(s"${envriConf.metaItemPrefix}resources/")
	}

	private def getConfig(using envri: Envri) = envriConfigs(envri)

	private given (using envri: Envri): BaseUriProvider = baseResourceUriProviders(envri)

	private val icosBup: BaseUriProvider = baseResourceUriProviders(Envri.ICOS)

	def getIcosLikeStation(stationId: UriId) = getRelative(s"stations/", stationId)(using icosBup)
	def getEcosystemStation(id: EtcStationId) = getIcosLikeStation(etcStationUriId(id))

	def getPerson(firstName: String, lastName: String)(using Envri): IRI = getPerson(getPersonCpId(firstName, lastName))
	def getPerson(cpId: UriId)(using Envri): IRI = getRelativeRaw("people/" + cpId)

//	def getEtcMembership(station: EtcStationId, roleId: String, lastName: String) = getRelative(
//		"memberships/", s"ES_${station.id}_${roleId}_$lastName"
//	)(icosBup)

	def getInstrDeployment(deplId: UriId)(using Envri): IRI = getRelative("deployments/", deplId)
	def getMembership(membId: UriId)(using Envri): IRI = getRelative("memberships/", membId)
	def getMembership(orgId: UriId, role: Role, lastName: String)(using Envri): IRI =
		getMembership(UriId(s"${orgId}_${role.name}_${UriId.escaped(lastName)}"))

	def getFunding(fundId: UriId)(using Envri): IRI = getRelative("fundings/", fundId)

	def getRole(role: Role)(using Envri) = getRelative(RolesPrefix, UriId(role.name))

	def getOrganization(orgId: UriId)(using Envri) = getRelative("organizations/", orgId)

	def getIcosInstrument(id: UriId) = getRelative("instruments/", id)(using icosBup)
	def getEtcInstrument(station: Int, id: Int) = getIcosInstrument{
		instrCpId(getEtcInstrTcId(station, id))(TcConf.EtcConf)
	}

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(UriId.apply).map(getOrganization(_)(using Envri.ICOS))

	val icosProject = getRelativeRaw("projects/icos")(using icosBup)
	val atmoTheme = getRelativeRaw("themes/atmosphere")(using icosBup)
	val oceanTheme = getRelativeRaw("themes/ocean")(using icosBup)

	def getAncillaryEntry(valueId: String) = getRelativeRaw("ancillary/" + valueId)(using icosBup)

	def getStaticObject(hash: Sha256Sum)(using Envri) = factory.createIRI(staticObjLandingPage(hash)(getConfig).toString)
	def getCollection(hash: Sha256Sum)(using Envri) = factory.createIRI(staticCollLandingPage(hash)(getConfig).toString)

	def getStaticObjectAccessUrl(hash: Sha256Sum)(using Envri) = staticObjAccessUrl(hash)(getConfig)

	def getAcquisition(hash: Sha256Sum)(using Envri) = getRelativeRaw(AcqPrefix + hash.id)
	def getProduction(hash: Sha256Sum)(using Envri) = getRelativeRaw(ProdPrefix + hash.id)
	def getSubmission(hash: Sha256Sum)(using Envri) = getRelativeRaw(SubmPrefix + hash.id)
	def getSpatialCoverage(hash: Sha256Sum)(using Envri): IRI = getSpatialCoverage(UriId(hash.id))
	def getSpatialCoverage(id: UriId)(using Envri) = getRelativeRaw(SpatCovPrefix + id.urlSafeString)
	def getVarInfo(hash: Sha256Sum, varLabel: String)(using Envri) = getRelativeRaw(s"${VarInfoPrefix}${urlEncode(varLabel)}_${hash.id}")
	def getPosition(pos: Position)(using Envri) = getRelativeRaw(s"position_${pos.lat6}_${pos.lon6}")

	def getObjectSpecification(lastSegment: UriId)(using envri: Envri) =
		if(envri == Envri.ICOS) getRelative("cpmeta/", lastSegment)
		else getRelative("objspecs/", lastSegment)

	def lookupIcosDatasetVar(varName: String): Option[IRI] =
		if("""^SWC_\d{1,2}_\d{1,2}_\d{1,2}$""".r.matches(varName))
			Some(getRelativeRaw("cpmeta/SWC_n_n_n")(using icosBup))
		else None
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
	val CCBY4 = new URI("https://creativecommons.org/licenses/by/4.0")

	object Acquisition{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, AcqPrefix)
	}

	object Submission{
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, SubmPrefix)
	}

	object SpatialCoverage{
		def unapply(uri: URI): Option[Sha256Sum] = asPrefWithHashSuff(uri.getPath.split('/').last, SpatCovPrefix)
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

	def etcStationUriId(station: EtcStationId) = TcConf.stationId[ETC.type](UriId.escaped(station.id))
	def getEtcInstrTcId(station: Int, id: Int): TcId[ETC.type] = TcConf.EtcConf.makeId(s"${station}_$id")
	def instrCpId[T <: TC : TcConf](tcId: TcId[T]): UriId = TcConf.tcScopedId(UriId.escaped(tcId.id))

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
