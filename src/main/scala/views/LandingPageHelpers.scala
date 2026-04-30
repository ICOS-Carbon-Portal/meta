package se.lu.nateko.cp.meta.views

import scala.language.unsafeNulls

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import se.lu.nateko.cp.doi.meta.Person as DoiMetaPerson
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.utils.urlEncode
import spray.json.*

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.jdk.CollectionConverters.IterableHasAsJava

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
		def isPreviewable(using vocab: CpVocab): Boolean =
			dobj.specificInfo.fold(
				spatioTemporal => spatioTemporal.variables.nonEmpty,
				stationTimeSeries => stationTimeSeries.columns.nonEmpty
			) ||
			dobj.specification.self.uri === vocab.cfCompliantNetcdfSpec ||
			dobj.specification.format.self.uri === CpmetaVocab.icosMultiImageZipUri ||
			dobj.specification.format.self.uri === CpmetaVocab.sitesMultiImageZipUri

		def previewEnabled(using vocab: CpVocab): Boolean =
			dobj.submission.stop.fold(false)(_.isBefore(Instant.now)) && dobj.isPreviewable

	def agentString(a: Agent): String = a match {
		case person: Person =>
			person.firstName + " " + person.lastName
		case org: Organization =>
			org.name
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
		.nameIdentifiers.flatMap: ni =>
			if isUriNaive(ni.nameIdentifier) then
				Some(ni.nameIdentifier)
			else ni.scheme.schemeUri.map: uri =>
				Seq(uri.stripSuffix("/"), ni.nameIdentifier).mkString("/")
		.headOption

	def getDoiTitle(refs: References): Option[String] =
		refs.doi.flatMap(_.titles.map(_.head)).map(_.title)

	def getPreviewURL(hash: Sha256Sum, variable: Option[String] = None)(using conf: EnvriConfig) = {
		val yAxis = variable.fold("")(v => s""","yAxis":"${v}"""")
		val params = urlEncode(s"""{"route":"preview","preview":["${hash.id}"]${yAxis}}""")

		s"""https://${conf.dataHost}/portal/#${params}"""
	}

	def getDownloadURL(dobj: DataObject)(using conf: EnvriConfig) = {
		val query = urlEncode(s"""ids=["${dobj.hash.id}"]&fileName=${dobj.fileName.replaceAll("\\.[^.]*$", "")}""")
		s"https://${conf.dataHost}/objects?${query}"
	}

	def renderMarkdown(md: String): String = {
		val extensions = Iterable(AutolinkExtension.create()).asJava
		val parser: Parser = Parser.builder().extensions(extensions).build()
		val document: Node = parser.parse(md)
		val renderer: HtmlRenderer = HtmlRenderer.builder().extensions(extensions).build()
		renderer.render(document)
	}

	extension(station: Station)
		def isDiscontinued: Boolean =
			station.specificInfo match
				case s: IcosStationSpecifics => s.discontinued
				case s: SitesStationSpecifics => s.discontinued
				case _ => false

	private def isUriNaive(s: String): Boolean =
		s.startsWith("https://") || s.startsWith("http://")
end LandingPageHelpers
