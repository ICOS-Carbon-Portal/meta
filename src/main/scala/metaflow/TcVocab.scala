package se.lu.nateko.cp.meta.metaflow

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.etcupload.StationId as EtcStationId
import se.lu.nateko.cp.meta.metaflow.icos.{ETC, EtcConf}
import se.lu.nateko.cp.meta.services.CpVocab

/**
 * TC-scoped URI minting, split out of `CpVocab` so that the core vocabulary can live
 * alongside `rdfStore`'s shared code without depending on the metaflow model. Every
 * member here mints or matches persistent URIs that already exist in the production
 * store, so string values must stay byte-identical to their pre-split form.
 */
class TcVocab(vocab: CpVocab):
	import TcVocab.*

	def getMembership(orgId: UriId, role: Role, lastName: String)(using Envri): IRI =
		vocab.getMembership(UriId(s"${orgId}_${role.name}_${UriId.escaped(lastName)}"))

	def getRole(role: Role)(using Envri): IRI =
		vocab.resourceUri(CpVocab.RolesPrefix, UriId(role.name))

	def getEcosystemStation(id: EtcStationId): IRI =
		vocab.getStation(etcStationUriId(id))(using Envri.ICOS)

	def getEtcInstrument(station: Int, id: Int): IRI =
		vocab.getInstrument(instrCpId(getEtcInstrTcId(station, id))(EtcConf))(using Envri.ICOS)

object TcVocab:

	def etcStationUriId(station: EtcStationId): UriId = TcConf.stationId[ETC.type](UriId.escaped(station.id))
	def getEtcInstrTcId(station: Int, id: Int): TcId[ETC.type] = EtcConf.makeId(s"${station}_$id")
	def instrCpId[T <: TC : TcConf](tcId: TcId[T]): UriId = TcConf.tcScopedId(UriId.escaped(tcId.id))
