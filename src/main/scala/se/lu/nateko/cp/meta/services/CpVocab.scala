package se.lu.nateko.cp.meta.services

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import se.lu.nateko.cp.meta.api.{CustomVocab, UriId}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs, IcosStationSpecifics, Position, collectionPrefix, objectPathPrefix, objectPrefix, staticCollLandingPage, staticObjAccessUrl, staticObjLandingPage}
import se.lu.nateko.cp.meta.core.etcupload.StationId as EtcStationId
import se.lu.nateko.cp.meta.metaflow.icos.{ETC, EtcConf}
import se.lu.nateko.cp.meta.metaflow.{Role, TC, TcConf, TcId}
import se.lu.nateko.cp.meta.utils.rdf4j.===

import java.net.URI

class CpVocab (val factory: ValueFactory)(using envriConfigs: EnvriConfigs) extends CustomVocab:
	import CpVocab.*
	import CustomVocab.urlEncode

	private val baseResourceUriProviders: Map[Envri, BaseUriProvider] = envriConfigs.map{
		case (envri, envriConf) =>
			envri -> makeUriProvider(s"${envriConf.metaItemPrefix}resources/")
	}

	private given (using envri: Envri): EnvriConfig = envriConfigs(envri)

	private given (using envri: Envri): BaseUriProvider = baseResourceUriProviders(envri)

	private val icosBup: BaseUriProvider = baseResourceUriProviders(Envri.ICOS)

	def getStation(stationId: UriId)(using Envri) = getRelative(s"stations/", stationId)
	def getEcosystemStation(id: EtcStationId) = getStation(etcStationUriId(id))(using Envri.ICOS)

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

	def getInstrument(id: UriId)(using Envri) = getRelative("instruments/", id)
	def getEtcInstrument(station: Int, id: Int) = getInstrument{
		instrCpId(getEtcInstrTcId(station, id))(EtcConf)
	}(using Envri.ICOS)

	val Seq(atc, etc, otc, cp, cal) = Seq("ATC", "ETC", "OTC", "CP", "CAL").map(UriId.apply).map(getOrganization(_)(using Envri.ICOS))

	val Seq(icosProject, atmoTheme, ecoTheme, oceanTheme) = Seq(
		"projects/icos", "themes/atmosphere", "themes/ecosystem", "themes/ocean"
	).map(getRelativeRaw(_)(using icosBup))

	def hasOceanTheme(icosSpec: IcosStationSpecifics): Boolean = icosSpec.theme.exists(_.self.uri === oceanTheme)

	def getAncillaryEntry(valueId: String) = getRelativeRaw("ancillary/" + valueId)(using icosBup)

	def getStaticObject(hash: Sha256Sum)(using Envri) = factory.createIRI(staticObjLandingPage(hash).toString)
	def getCollection(hash: Sha256Sum)(using Envri) = factory.createIRI(staticCollLandingPage(hash).toString)

	def getStaticObjectAccessUrl(hash: Sha256Sum)(using Envri) = staticObjAccessUrl(hash)

	def getAcquisition(hash: Sha256Sum)(using Envri) = getRelativeRaw(AcqPrefix + hash.id)
	def getProduction(hash: Sha256Sum)(using Envri) = getRelativeRaw(ProdPrefix + hash.id)
	def getProductionContribList(hash: Sha256Sum)(using Envri) = getRelativeRaw(ProdPrefix + ContribsPrefix + hash.id)
	def getDocContribList(hash: Sha256Sum)(using Envri) = getRelativeRaw(ContribsPrefix + hash.id)
	def getSubmission(hash: Sha256Sum)(using Envri) = getRelativeRaw(SubmPrefix + hash.id)
	def getSpatialCoverage(hash: Sha256Sum)(using Envri): IRI = getSpatialCoverage(UriId(hash.id))
	def getSpatialCoverage(id: UriId)(using Envri) = getRelativeRaw(SpatCovPrefix + id.urlSafeString)
	def getVarInfo(hash: Sha256Sum, varLabel: String)(using Envri) = getRelativeRaw(s"${VarInfoPrefix}${urlEncode(varLabel)}_${hash.id}")
	def getPosition(pos: Position)(using Envri) = getRelativeRaw(s"position_${pos.lat6}_${pos.lon6}")

	def getNextVersionColl(hash: Sha256Sum)(using Envri) = getRelativeRaw(NextVersionCollPrefix + hash.id)

	def getObjectSpecification(lastSegment: UriId)(using envri: Envri) =
		val suffix: String = envri match
			case Envri.ICOS | Envri.ICOSCities => "cpmeta/"
			case Envri.SITES =>  "objspecs/"
		getRelative(suffix, lastSegment)

	def lookupIcosEcoDatasetVar(varName: String): Option[IRI] = getEcoVariableFamily(varName).map: fam =>
		getRelativeRaw(s"cpmeta/${fam}_n_n_n")(using icosBup)

	val atmGhgProdSpec = getObjectSpecification(UriId("atmGhgProduct"))(using Envri.ICOS)
	val cfCompliantNetcdfSpec = getObjectSpecification(UriId("arbitraryCfNetcdf"))(using Envri.ICOS)

end CpVocab


object CpVocab{
	import CustomVocab.urlEncode
	import Sha256Sum.IdLength

	val NextVersionCollPrefix = "nextvcoll_"
	val AcqPrefix = "acq_"
	val ProdPrefix = "prod_"
	val ContribsPrefix = "contribs_"
	val SubmPrefix = "subm_"
	val SpatCovPrefix = "spcov_"
	val VarInfoPrefix = "varinfo_"
	val RolesPrefix = "roles/"
	val CCBY4 = new URI("https://creativecommons.org/licenses/by/4.0")
	val LabeledStationStatus = "STEP3APPROVED"
	val EcoVarFamilyRegex = """^(.+)_\d{1,2}_\d{1,2}_\d{1,2}$""".r
	val KnownEcoVarFamilies = Set(
		"SWC", "TS", "LW_IN", "LW_OUT", "SW_IN", "SW_OUT", "TA", "RH", "PPFD_IN", "PPFD_OUT",
		"PPFD_BC_IN", "PPFD_BC_OUT", "PPFD_DIF", "SW_DIF", "G", "WTD", "PA", "P", "D_SNOW", "P_SNOW",
		"WS", "WD"
	)

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

	object IcosRole{
		def unapply(iri: IRI): Option[Role] = {
			val segms = iri.stringValue.split('/')
			if(segms.length < 2 || !RolesPrefix.startsWith(segms(segms.length - 2))) None else{
				Role.forName(segms.last)
			}
		}
	}

	object NextVersColl:
		def unapply(iri: IRI): Option[Sha256Sum] = asPrefWithHash(iri, NextVersionCollPrefix)

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

	def etcStationUriId(station: EtcStationId) = TcConf.stationId[ETC.type](UriId.escaped(station.id))
	def getEtcInstrTcId(station: Int, id: Int): TcId[ETC.type] = EtcConf.makeId(s"${station}_$id")
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

	def getEcoVariableFamily(varName: String): Option[String] = varName match
		case EcoVarFamilyRegex(famName) if KnownEcoVarFamilies.contains(famName) => Some(famName)
		case _ => None

}
