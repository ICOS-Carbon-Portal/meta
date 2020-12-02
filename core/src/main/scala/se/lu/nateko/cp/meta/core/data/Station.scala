package se.lu.nateko.cp.meta.core.data

import java.net.URI
import java.time.LocalDate

case class Station(
	org: Organization,
	id: String,
	coverage: Option[GeoFeature],
	responsibleOrganization: Option[Organization],
	website: Option[URI],
	pictures: Seq[URI],
	specificInfo: StationSpecifics
)

sealed trait StationSpecifics

case object NoStationSpecifics extends StationSpecifics

sealed trait EcoStationSpecifics extends StationSpecifics{
	def climateZone: Option[UriResource]
	def meanAnnualTemp: Option[Float]
	def meanAnnualPrecip: Option[Float]
}

case class SitesStationSpecifics(
	sites: Seq[Site],
	ecosystems: Seq[UriResource],
	climateZone: Option[UriResource],
	meanAnnualTemp: Option[Float],
	meanAnnualPrecip: Option[Float],
	operationalPeriod: Option[String],
	documentation: Seq[PlainStaticObject]
) extends EcoStationSpecifics

sealed trait IcosStationSpecifics extends StationSpecifics{
	def stationClass: IcosStationClass.Value
	def labelingDate: Option[LocalDate]
}

case class EtcStationSpecifics(
	stationClass: IcosStationClass.Value,
	labelingDate: Option[LocalDate],
	climateZone: Option[UriResource],
	ecosystemType: Option[UriResource],
	meanAnnualTemp: Option[Float],
	meanAnnualPrecip: Option[Float]
) extends IcosStationSpecifics with EcoStationSpecifics

object IcosStationClass extends Enumeration{
	val One = Value("1")
	val Two = Value("2")
	val Associated = Value
}
