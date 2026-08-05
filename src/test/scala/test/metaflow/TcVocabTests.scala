package se.lu.nateko.cp.meta.test.metaflow

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.core.etcupload.StationId as EtcStationId
import se.lu.nateko.cp.meta.metaflow.{PI, Researcher, TcVocab}
import se.lu.nateko.cp.meta.services.CpVocab

import java.net.URI

/**
 * Baseline for the persistent-URI-minting members that task 08 of the rdfCommon split
 * relocated from `CpVocab` (rdfStore) to `TcVocab` (meta): `getMembership(orgId, role,
 * lastName)`, `getRole`, `etcStationUriId`, `getEtcInstrTcId` and `instrCpId`. Every string
 * asserted here must stay byte-identical to what `CpVocab` produced before the move, since
 * these URIs already exist in the production store.
 */
class TcVocabTests extends AnyFunSpec:

	given EnvriConfigs = Map(Envri.ICOS -> EnvriConfig(
		authHost = "cpauth.icos-cp.eu",
		dataHost = "data.icos-cp.eu",
		metaHost = "meta.icos-cp.eu",
		metaItemPrefix = new URI("http://meta.icos-cp.eu/"),
		dataItemPrefix = new URI("https://meta.icos-cp.eu/"),
		defaultTimezoneId = "UTC"
	))

	given Envri = Envri.ICOS

	val factory = SimpleValueFactory.getInstance()
	val vocab = new CpVocab(factory)
	val tcVocab = new TcVocab(vocab)

	def htm: EtcStationId = "SE-Htm" match
		case EtcStationId(s) => s

	describe("getMembership(orgId, role, lastName)"):
		it("mints the same URI shape as the pre-split CpVocab implementation"):
			val uri = tcVocab.getMembership(UriId("ATC"), Researcher, "Doe")
			assert(uri.stringValue === "http://meta.icos-cp.eu/resources/memberships/ATC_Researcher_Doe")

	describe("getRole"):
		it("mints a URI under the roles/ namespace using the role's name"):
			val uri = tcVocab.getRole(PI)
			assert(uri.stringValue === "http://meta.icos-cp.eu/resources/roles/PI")

	describe("etcStationUriId"):
		it("prefixes the escaped ETC station id with the ETC station prefix"):
			assert(TcVocab.etcStationUriId(htm) === UriId("ES_SE-Htm"))

	describe("getEtcInstrTcId"):
		it("combines station and logger id into the ETC TcId format"):
			assert(TcVocab.getEtcInstrTcId(38, 2).id === "38_2")

	describe("instrCpId"):
		it("tc-scopes the escaped tcId using the TcConf's tcPrefix"):
			val tcId = TcVocab.getEtcInstrTcId(38, 2)
			assert(TcVocab.instrCpId(tcId)(using se.lu.nateko.cp.meta.metaflow.icos.EtcConf) === UriId("ETC_38_2"))

	describe("getEcosystemStation"):
		it("mints the same station URI as CpVocab.getStation(etcStationUriId(id)) used to"):
			val uri = tcVocab.getEcosystemStation(htm)
			assert(uri.stringValue === "http://meta.icos-cp.eu/resources/stations/ES_SE-Htm")

	describe("getEtcInstrument"):
		it("mints the same instrument URI as CpVocab.getEtcInstrument used to"):
			val uri = tcVocab.getEtcInstrument(38, 2)
			assert(uri.stringValue === "http://meta.icos-cp.eu/resources/instruments/ETC_38_2")
