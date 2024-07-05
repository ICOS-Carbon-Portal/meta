package se.lu.nateko.cp.meta.core.data

import java.net.URI
import java.time.LocalDate
import scala.util.Try
import spray.json.*
import se.lu.nateko.cp.meta.core.CommonJsonSupport

case class Station(
	org: Organization,
	id: String,
	location: Option[Position],
	coverage: Option[GeoFeature],
	responsibleOrganization: Option[Organization],
	pictures: Seq[URI],
	specificInfo: StationSpecifics,
	countryCode: Option[CountryCode],
	funding: Option[Seq[Funding]]
){
	def fullCoverage: Option[GeoFeature] = List(location, coverage).flatten match{
		case Nil => None
		case single :: Nil => Some(single)
		case multiple => Some(FeatureCollection(multiple, Some(org.name), None).flatten) // ?
	}

	def timeZoneOffset: Option[Int] = specificInfo match
		case icos: IcosStationSpecifics => icos.timeZoneOffset
		case cities: IcosCitiesStationSpecifics => cities.timeZoneOffset
		case _: SitesStationSpecifics | NoStationSpecifics => None
}

case class Funding(
	self: UriResource,
	funder: Funder,
	awardTitle: Option[String],
	awardNumber: Option[String],
	awardUrl: Option[URI],
	start: Option[LocalDate],
	stop: Option[LocalDate],
)

enum FunderIdType:
	case `Crossref Funder ID`, GRID, ISNI, ROR, Other

case class Funder(org: Organization, id: Option[(String, FunderIdType)])

case object NoStationSpecifics extends StationSpecifics

sealed trait EcoStationSpecifics extends StationSpecifics{
	def climateZone: Option[UriResource]
	def ecosystems: Seq[UriResource]
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
	discontinued: Boolean,
	documentation: Seq[PlainStaticObject]
) extends EcoStationSpecifics

sealed trait IcosStationSpecifics extends StationSpecifics{
	def theme: Option[DataTheme]
	def stationClass: Option[IcosStationClass]
	def labelingDate: Option[LocalDate]
	def discontinued: Boolean
	def timeZoneOffset: Option[Int]
	def documentation: Seq[PlainStaticObject]
}

sealed trait StationSpecifics

case class AtcStationSpecifics(
	wigosId: Option[String],
	theme: Option[DataTheme],
	stationClass: Option[IcosStationClass],
	labelingDate: Option[LocalDate],
	discontinued: Boolean,
	timeZoneOffset: Option[Int],
	documentation: Seq[PlainStaticObject]
) extends IcosStationSpecifics

object AtcStationSpecifics{
	def apply(base: IcosStationSpecifics, wigosId: Option[String]): AtcStationSpecifics = AtcStationSpecifics(
		wigosId = wigosId,
		theme = base.theme,
		stationClass = base.stationClass,
		labelingDate = base.labelingDate,
		discontinued = base.discontinued,
		timeZoneOffset = base.timeZoneOffset,
		documentation = base.documentation
	)
}

case class OtcStationSpecifics(
	theme: Option[DataTheme],
	stationClass: Option[IcosStationClass],
	labelingDate: Option[LocalDate],
	discontinued: Boolean,
	timeZoneOffset: Option[Int],
	documentation: Seq[PlainStaticObject]
) extends IcosStationSpecifics

case class EtcStationSpecifics(
	theme: Option[DataTheme],
	stationClass: Option[IcosStationClass],
	labelingDate: Option[LocalDate],
	discontinued: Boolean,
	climateZone: Option[UriResource],
	ecosystemType: Option[UriResource],
	meanAnnualTemp: Option[Float],
	meanAnnualPrecip: Option[Float],
	meanAnnualRad: Option[Float],
	stationDocs: Seq[URI],
	stationPubs: Seq[URI],
	timeZoneOffset: Option[Int],
	documentation: Seq[PlainStaticObject]
) extends IcosStationSpecifics with EcoStationSpecifics{
	override def ecosystems = ecosystemType.toSeq
}

case class IcosCitiesStationSpecifics(timeZoneOffset: Option[Int]) extends StationSpecifics

object EtcStationSpecifics{
	def apply(base: IcosStationSpecifics): EtcStationSpecifics = EtcStationSpecifics(
		theme = base.theme,
		stationClass = base.stationClass,
		labelingDate = base.labelingDate,
		discontinued = base.discontinued,
		climateZone = None,
		ecosystemType = None,
		meanAnnualTemp = None,
		meanAnnualPrecip = None,
		meanAnnualRad = None,
		stationDocs = Nil,
		stationPubs = Nil,
		timeZoneOffset = base.timeZoneOffset,
		documentation = base.documentation
	)
}

enum IcosStationClass:
	case `1`, `2`, Associated
