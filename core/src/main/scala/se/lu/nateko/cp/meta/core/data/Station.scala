package se.lu.nateko.cp.meta.core.data

import java.net.URI
import java.time.LocalDate
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._

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
	def stationClass: Option[IcosStationClass.Value]
	def labelingDate: Option[LocalDate]
	def countryCode: Option[CountryCode]
}

case class PlainIcosSpecifics(
	stationClass: Option[IcosStationClass.Value],
	labelingDate: Option[LocalDate],
	countryCode: Option[CountryCode]
) extends IcosStationSpecifics

case class EtcStationSpecifics(
	stationClass: Option[IcosStationClass.Value],
	labelingDate: Option[LocalDate],
	countryCode: Option[CountryCode],
	climateZone: Option[UriResource],
	ecosystemType: Option[UriResource],
	meanAnnualTemp: Option[Float],
	meanAnnualPrecip: Option[Float],
	meanAnnualRad: Option[Float]
) extends IcosStationSpecifics with EcoStationSpecifics

object IcosStationClass extends Enumeration{
	val One = Value("1")
	val Two = Value("2")
	val Associated = Value
}

object StationSpecifics extends CommonJsonSupport{
	import JsonSupport.{uriResourceFormat, plainStaticObjectFormat, siteFormat, countryCodeFormat}
	import CommonJsonSupport._
	implicit val stationClassFormat = enumFormat(IcosStationClass)
	implicit val etcStationSpecificsFormat = jsonFormat8(EtcStationSpecifics)
	implicit val sitesStationSpecificsFormat = jsonFormat6(SitesStationSpecifics)

	private val EtcSpec = "etc"
	private val SitesSpec = "sites"
	implicit object stationSpecificsFormat extends RootJsonFormat[StationSpecifics]{
		def write(ss: StationSpecifics): JsValue = ss match{
			case NoStationSpecifics => JsObject.empty
			case etc: EtcStationSpecifics => etc.toTypedJson(EtcSpec)
			case sites: SitesStationSpecifics => sites.toTypedJson(SitesSpec)
		}

		def read(value: JsValue) =
			value.asJsObject("StationSpecifics must be a JSON object").fields.get(TypeField) match{
				case Some(JsString(EtcSpec)) => value.convertTo[EtcStationSpecifics]
				case Some(JsString(SitesSpec)) =>  value.convertTo[SitesStationSpecifics]
				case None => NoStationSpecifics
				case Some(unknType) => deserializationError(s"Unknown StationSpecifics type $unknType")
			}
	}
}
