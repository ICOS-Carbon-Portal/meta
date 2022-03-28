package se.lu.nateko.cp.meta.views

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import spray.json._
import java.net.URI


object LandingPageHelpers{

	def printToJson(dataObj: DataObject): String = dataObj.toJson.prettyPrint

	private def formatDateTime(inst: Instant)(implicit conf: EnvriConfig): String = {
		val formatter: DateTimeFormatter = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.of(conf.defaultTimezoneId))

		formatter.format(inst)
	}

	implicit class PresentableInstant(val inst: Instant) extends AnyVal{
		def getDateTimeStr(implicit conf: EnvriConfig): String = formatDateTime(inst)
	}

	implicit class OptionalInstant(val inst: Option[Instant]) extends AnyVal{
		def getDateTimeStr(implicit conf: EnvriConfig): String = {
			inst match {
				case Some(i) => formatDateTime(i)
				case None => "Not done"
			}
		}
	}

	def agentString(a: Agent): String = a match {
		case person: Person =>
			person.firstName + " " + person.lastName
		case org: Organization =>
			org.name
	}
	implicit object AgentOrdering extends Ordering[Agent]{

		override def compare(a1: Agent, a2: Agent): Int = {
			val majorComp = typeComp(a1, a2)
			if(majorComp == 0)
				implicitly[Ordering[String]].compare(agentString(a1), agentString(a2))
			else majorComp
		}

		private def typeComp(a1: Agent, a2: Agent): Int = (a1, a2) match {
			case (_: Organization, _: Person) => 1 //people are listed before orgs
			case (_: Person, _: Organization) => -1
			case _ => 0
		}
	}

	implicit val uriResourceOrdering = Ordering.by[UriResource, String]{res =>
		res.label.getOrElse(res.uri.getPath.split('/').last)
	}

	implicit val plainStaticObjectOrdering = Ordering.by[PlainStaticObject, String](_.name)

	def stationUriShortener(uri: URI): String = {
		val icosStationPref = "http://meta.icos-cp.eu/resources/stations/"
		val wdcggStationPref = "http://meta.icos-cp.eu/resources/wdcgg/station/"
		val sitesStationPref = "https://meta.fieldsites.se/resources/stations/"
		val uriStr = uri.toString
		if(uriStr.startsWith(icosStationPref))
			"i" + uriStr.stripPrefix(icosStationPref)
		else if(uriStr.startsWith(wdcggStationPref))
			"w" + uriStr.stripPrefix(wdcggStationPref)
		else if(uriStr.startsWith(sitesStationPref))
			uriStr.stripPrefix(sitesStationPref)
		else
			uriStr
	}

}
