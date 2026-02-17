package se.lu.nateko.cp.meta.test

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.{EtcStationSpecifics, Station}
import se.lu.nateko.cp.meta.core.tests.TestFactory.given
import se.lu.nateko.cp.meta.metaflow.TcStation
import se.lu.nateko.cp.meta.metaflow.icos.{ETC, EtcConf}
import se.lu.nateko.cp.meta.core.data.Organization
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.CountryCode
import se.lu.nateko.cp.meta.core.data.StationSpecifics

object MetaTestFactory:

	given Arbitrary[UriId] = Arbitrary(
		Gen.alphaNumStr.map(UriId(_))
	)

	def stationWithSpecifics(specifics: StationSpecifics): Gen[Station] = {
		arbitrary[Station].map(_.copy(specificInfo = specifics))
	}

	given Arbitrary[TcStation[ETC.type]] = {
		Arbitrary(
			for
				cpId <- arbitrary[UriId]
				tcId <- Gen.alphaNumStr.map(EtcConf.makeId)
				specifics <- arbitrary[EtcStationSpecifics]
				core <- stationWithSpecifics(specifics)
			yield TcStation(
				cpId = cpId,
				tcId = tcId,
				core = core,
				responsibleOrg = None,
				funding = Seq.empty,
				networks = Seq.empty
			)
		)
	}

	extension (station: TcStation[ETC.type]) {
		def withSpecifics(transformer: (EtcStationSpecifics => EtcStationSpecifics)): TcStation[ETC.type] = {
			val specifics = station.core.specificInfo.asInstanceOf[EtcStationSpecifics]
			station.copy(core = station.core.copy(specificInfo = transformer(specifics)))
		}
	}

end MetaTestFactory
