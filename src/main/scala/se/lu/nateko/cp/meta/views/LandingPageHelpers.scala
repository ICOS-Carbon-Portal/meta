package se.lu.nateko.cp.meta.views

import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.services.CpmetaVocab
import spray.json.*

import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import se.lu.nateko.cp.doi.meta.{Person => DoiMetaPerson}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.utils.urlEncode
import se.lu.nateko.cp.meta.utils.rdf4j.===

object LandingPageHelpers:

	def printToJson(dataObj: DataObject): String = dataObj.toJson.prettyPrint

	private def formatDateTime(inst: Instant)(using conf: EnvriConfig): String = {
		val formatter: DateTimeFormatter = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.of(conf.defaultTimezoneId))

		formatter.format(inst)
	}

	extension (inst: Instant)
		def getDateTimeStr(using EnvriConfig): String = formatDateTime(inst)


	extension (inst: Option[Instant]){
		def getDateTimeStr(using EnvriConfig): String = {
			inst match {
				case Some(i) => formatDateTime(i)
				case None => "Not done"
			}
		}
	}

	extension (dobj: DataObject)
		def hasSupportingPreviewApp: Boolean = dobj.isPreviewable ||
			dobj.specification.format.self.uri === CpmetaVocab.icosMultiImageZipUri ||
			dobj.specification.format.self.uri === CpmetaVocab.sitesMultiImageZipUri

	def agentString(a: Agent): String = a match {
		case person: Person =>
			person.firstName + " " + person.lastName
		case org: Organization =>
			org.name
	}
	given Ordering[Agent] with{

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

	given Ordering[UriResource] = Ordering.by[UriResource, String]{res =>
		res.label.getOrElse(res.uri.getPath.split('/').last)
	}

	given Ordering[PlainStaticObject] = Ordering.by[PlainStaticObject, String](_.name)

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

	def doiAgentUri(agent: DoiMetaPerson): Option[String] = agent
		.nameIdentifiers.flatMap{ni =>
			ni.scheme.schemeUri.map(uri => Seq(uri.stripSuffix("/"), ni.nameIdentifier).mkString("/"))
		}.headOption

	def getDoiTitle(refs: References): Option[String] =
		refs.doi.flatMap(_.titles.map(_.head)).map(_.title)

	def getPreviewURL(hash: Sha256Sum, variable: Option[String] = None)(using conf: EnvriConfig) = {
		val yAxis = variable.fold("")(v => s""","yAxis":"${v}"""")
		val params = urlEncode(s"""{"route":"preview","preview":["${hash.id}"]${yAxis}}""")

		s"""https://${conf.dataHost}/portal/#${params}"""
	}

end LandingPageHelpers
