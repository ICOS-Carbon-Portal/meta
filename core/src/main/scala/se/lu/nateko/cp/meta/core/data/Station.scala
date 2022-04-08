package se.lu.nateko.cp.meta.core.data

import java.net.URI
import java.time.LocalDate
import scala.util.Try
import spray.json._
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.toTypedJson

case class Station(
	org: Organization,
	id: String,
	location: Option[Position],
	coverage: Option[GeoFeature],
	responsibleOrganization: Option[Organization],
	pictures: Seq[URI],
	specificInfo: StationSpecifics,
	funding: Option[Seq[Funding]]
){
	def fullCoverage: Option[GeoFeature] = List(location, coverage).flatten match{
		case Nil => None
		case single :: Nil => Some(single)
		case multiple => Some(FeatureCollection(multiple, Some(org.name)).flatten)
	}
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

object FunderIdType extends Enumeration{
	type FunderIdType = Value
	val Crossref = Value("Crossref Funder ID")
	val GRID, ISNI, ROR, Other = Value
}

case class Funder(org: Organization, id: Option[(String,FunderIdType.Value)])

sealed trait StationSpecifics

case object NoStationSpecifics extends StationSpecifics

sealed trait EcoStationSpecifics extends StationSpecifics{
	def climateZone: Option[UriResource]
	def ecosystems: Seq[UriResource]
	def meanAnnualTemp: Option[Float]
}

case class SitesStationSpecifics(
	sites: Seq[Site],
	ecosystems: Seq[UriResource],
	climateZone: Option[UriResource],
	meanAnnualTemp: Option[Float],
	operationalPeriod: Option[String],
	documentation: Seq[PlainStaticObject]
) extends EcoStationSpecifics

sealed trait IcosStationSpecifics extends StationSpecifics{
	def theme: Option[DataTheme]
	def stationClass: Option[IcosStationClass.Value]
	def labelingDate: Option[LocalDate]
	def discontinued: Boolean
	def countryCode: Option[CountryCode]
	def timeZoneOffset: Option[Int]
	def documentation: Seq[PlainStaticObject]
}

case class PlainIcosSpecifics(
	theme: Option[DataTheme],
	stationClass: Option[IcosStationClass.Value],
	labelingDate: Option[LocalDate],
	discontinued: Boolean,
	countryCode: Option[CountryCode],
	timeZoneOffset: Option[Int],
	documentation: Seq[PlainStaticObject]
) extends IcosStationSpecifics

case class EtcStationSpecifics(
	theme: Option[DataTheme],
	stationClass: Option[IcosStationClass.Value],
	labelingDate: Option[LocalDate],
	discontinued: Boolean,
	countryCode: Option[CountryCode],
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

object IcosStationClass extends Enumeration{
	type IcosStationClass = Value
	val One = Value("1")
	val Two = Value("2")
	val Associated = Value("Associated")
	def parse(s: String): Try[Value] = Try(withName(s))
}

object StationSpecifics extends CommonJsonSupport{
	import JsonSupport.given
	import CommonJsonSupport.TypeField

	given JsonFormat[IcosStationClass.IcosStationClass] = enumFormat(IcosStationClass)
	given RootJsonFormat[EtcStationSpecifics] = jsonFormat14(EtcStationSpecifics.apply)
	given RootJsonFormat[SitesStationSpecifics] = jsonFormat6(SitesStationSpecifics.apply)
	given RootJsonFormat[PlainIcosSpecifics] = jsonFormat7(PlainIcosSpecifics.apply)

	private val EtcSpec = "etc"
	private val SitesSpec = "sites"
	private val PlainIcosSpec = "plainicos"
	given RootJsonFormat[StationSpecifics] with{
		def write(ss: StationSpecifics): JsValue = ss match{
			case NoStationSpecifics => JsObject.empty
			case etc: EtcStationSpecifics => etc.toTypedJson(EtcSpec)
			case sites: SitesStationSpecifics => sites.toTypedJson(SitesSpec)
			case icos: PlainIcosSpecifics => icos.toTypedJson(PlainIcosSpec)
		}

		def read(value: JsValue) =
			value.asJsObject("StationSpecifics must be a JSON object").fields.get(TypeField) match{
				case Some(JsString(EtcSpec)) => value.convertTo[EtcStationSpecifics]
				case Some(JsString(SitesSpec)) => value.convertTo[SitesStationSpecifics]
				case Some(JsString(PlainIcosSpec)) => value.convertTo[PlainIcosSpecifics]
				case None => NoStationSpecifics
				case Some(unknType) => deserializationError(s"Unknown StationSpecifics type $unknType")
			}
	}
}
