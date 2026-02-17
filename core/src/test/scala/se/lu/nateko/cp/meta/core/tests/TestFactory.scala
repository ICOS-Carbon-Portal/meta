package se.lu.nateko.cp.meta.core.tests

import scala.language.unsafeNulls

import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary

import java.net.URI
import java.time.LocalDate

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*

object TestFactory:

	def make[T: Arbitrary]: T = arbitrary[T].sample.get

	given Arbitrary[URI] = Arbitrary(
		Gen.alphaNumStr.map(s => new URI(s"http://example.org/$s"))
	)

	given Arbitrary[LocalDate] = Arbitrary(
		for
			year <- Gen.choose(2000, 2030)
			month <- Gen.choose(1, 12)
			day <- Gen.choose(1, 28)
		yield LocalDate.of(year, month, day)
	)

	given Arbitrary[CountryCode] = Arbitrary(
		Gen.oneOf("SE", "DE", "FR", "IT", "ES", "DK", "FI", "NO")
			.map(CountryCode.unapply(_).get)
	)

	given Arbitrary[Sha256Sum] = Arbitrary(
		Gen.listOfN(32, Gen.choose(0.toByte, 127.toByte))
			.map(bytes => new Sha256Sum(bytes.toArray))
	)

	given Arbitrary[UriResource] = Arbitrary(
		for
			uri <- arbitrary[URI]
			label <- arbitrary[Option[String]]
			comments <- arbitrary[Seq[String]]
		yield UriResource(uri, label, comments)
	)

	given Arbitrary[Organization] = Arbitrary(
		for
			self <- arbitrary[UriResource]
			name <- arbitrary[String]
			email <- arbitrary[Option[String]]
			website <- arbitrary[Option[URI]]
		yield Organization(self, name, email, website, None)
	)

	given Arbitrary[Position] = Arbitrary(
		for
			lat <- Gen.choose(-90.0, 90.0)
			lon <- Gen.choose(-180.0, 180.0)
			alt <- arbitrary[Option[Float]]
			label <- arbitrary[Option[String]]
			uri <- arbitrary[Option[URI]]
		yield Position(lat, lon, alt, label, uri)
	)

	given Arbitrary[EtcStationSpecifics] = Arbitrary(
		for
			discontinued <- arbitrary[Boolean]
			timeZoneOffset <- arbitrary[Option[Int]]
		yield EtcStationSpecifics(
			theme = None,
			stationClass = None,
			labelingDate = None,
			discontinued = discontinued,
			climateZone = None,
			ecosystemType = None,
			meanAnnualTemp = None,
			meanAnnualPrecip = None,
			meanAnnualRad = None,
			stationDocs = Nil,
			stationPubs = Nil,
			timeZoneOffset = timeZoneOffset,
			documentation = Nil
		)
	)

	given Arbitrary[SitesStationSpecifics] = Arbitrary(
		for
			discontinued <- arbitrary[Boolean]
		yield SitesStationSpecifics(
			sites = Nil,
			ecosystems = Nil,
			climateZone = None,
			meanAnnualTemp = None,
			meanAnnualPrecip = None,
			operationalPeriod = None,
			discontinued = discontinued,
			documentation = Nil
		)
	)

	given Arbitrary[OtcStationSpecifics] = Arbitrary(
		for
			discontinued <- arbitrary[Boolean]
		yield OtcStationSpecifics(
			theme = None,
			stationClass = None,
			labelingDate = None,
			discontinued = discontinued,
			timeZoneOffset = None,
			documentation = Nil
		)
	)

	given Arbitrary[AtcStationSpecifics] = Arbitrary(
		for
			discontinued <- arbitrary[Boolean]
		yield AtcStationSpecifics(
			wigosId = None,
			theme = None,
			stationClass = None,
			labelingDate = None,
			discontinued = discontinued,
			timeZoneOffset = None,
			documentation = Nil
		)
	)

	given Arbitrary[IcosCitiesStationSpecifics] = Arbitrary(
		for
			tzOffset <- arbitrary[Option[Int]]
			network <- Gen.oneOf(CityNetwork.values.toSeq)
		yield IcosCitiesStationSpecifics(
			timeZoneOffset = tzOffset,
			network = network
		)
	)

	given Arbitrary[StationSpecifics] = Arbitrary(
		Gen.oneOf(
			Gen.const(NoStationSpecifics),
			arbitrary[SitesStationSpecifics],
			arbitrary[OtcStationSpecifics],
			arbitrary[AtcStationSpecifics],
			arbitrary[EtcStationSpecifics],
			arbitrary[IcosCitiesStationSpecifics]
		)
	)

	given Arbitrary[Station] = Arbitrary(
		for
			org <- arbitrary[Organization]
			id <- arbitrary[String]
			location <- arbitrary[Option[Position]]
			countryCode <- arbitrary[Option[CountryCode]]
			specificInfo <- arbitrary[StationSpecifics]
		yield Station(
			org = org,
			id = id,
			location = location,
			coverage = None,
			responsibleOrganization = None,
			pictures = Nil,
			specificInfo = specificInfo,
			countryCode = countryCode,
			funding = None,
			networks = Nil
		)
	)

end TestFactory
