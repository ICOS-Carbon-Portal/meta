package se.lu.nateko.cp.meta.services.citation

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZonedDateTime

import org.eclipse.rdf4j.repository.Repository

import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j._
import CitationStyle._
import scala.util.Failure
import scala.util.Success

case class CitationInfo(
	val pidUrl: Option[String],
	val authors: Option[Seq[Person]],
	val title: Option[String],
	val year: Option[String],
	val tempCovDisplay: Option[String],
	val citText: Option[String],
)

class CitationMaker(doiCiter: PlainDoiCiter, repo: Repository, coreConf: MetaCoreConfig) extends FetchingHelper {
	import CitationMaker._
	private implicit val envriConfs = coreConf.envriConfigs

	protected val server = new Rdf4jInstanceServer(repo)
	val vocab = new CpVocab(server.factory)
	protected val hasKeywords = (new CpmetaVocab(server.factory)).hasKeywords
	val attrProvider = new AttributionProvider(repo, vocab)

	def getCitationString(coll: StaticCollection): Option[String] = getDoiCitation(coll, CitationStyle.TEXT)


	def getCitationInfo(sobj: StaticObject)(implicit envri: Envri.Value): References = sobj match{

		case data: DataObject =>
			val citInfo = if (envri == Envri.SITES) getSitesCitation(data) else getIcosCitation(data)
			val dobj = vocab.getStaticObject(data.hash)
			val keywords = getOptionalString(dobj, hasKeywords).map(s => parseCommaSepList(s).toIndexedSeq)
			val structuredCitations = new StructuredCitations(data, citInfo, keywords)

			// citationString in APA format: https://owl.purdue.edu/owl/research_and_citation/apa_style/apa_formatting_and_style_guide/general_format.html
			References(
				citationString = getDoiCitation(data, CitationStyle.TEXT).orElse(citInfo.citText),
				citationBibTex = getDoiCitation(data, CitationStyle.BIBTEX).orElse(Some(structuredCitations.toBibTex)),
				citationRis = getDoiCitation(data, CitationStyle.BIBTEX).orElse(Some(structuredCitations.toRis)),
				authors = citInfo.authors,
				temporalCoverageDisplay = citInfo.tempCovDisplay,
				keywords = keywords
			)

		case doc: DocObject => References.empty.copy(
			citationString = getDoiCitation(doc, CitationStyle.TEXT),
			citationBibTex = getDoiCitation(doc, CitationStyle.BIBTEX),
			citationRis = getDoiCitation(doc, CitationStyle.BIBTEX)
		)
	}

	private def getDoiCitation(item: CitableItem, style: CitationStyle): Option[String] = item.doi.collect{
		case Doi(doi) => doiCiter.getCitationEager(doi, style) match{
			case None => "Fetching... Try again in a few seconds"
			case Some(Success(cit)) => cit
			case Some(Failure(err)) => "Error fetching DOI citation: " + err.getMessage
		}
	}

	private def getIcosCitation(dobj: DataObject): CitationInfo = {
		val zoneId = ZoneId.of("UTC")
		val tempCov = getTemporalCoverageDisplay(dobj, zoneId)
		val isIcosProject = dobj.specification.project.self.uri === vocab.icosProject

		def titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					time <- tempCov
				) yield {
					val station = acq.station.org.name
					val height = acq.samplingHeight.fold("")(sh => s" ($sh m)")
					s"$spec, $station$height, $time"
				}
		)

		val authors: Seq[Person] = if(isIcosProject) attrProvider.getAuthors(dobj) else{
			import AttributionProvider.personOrdering
			dobj.production.toSeq.flatMap(prod => prod.contributors :+ prod.creator).collect{
				case p: Person => p
			}.sorted.distinct
		}

		val pidUrlOpt = getPidUrl(dobj)
		val projName = if(isIcosProject) Some("ICOS RI") else dobj.specification.project.self.label
		val yearOpt = productionTime(dobj).map(prodInst => formatDate(prodInst, zoneId).take(4))

		val citText = for(
			title <- titleOpt;
			pidUrl <- pidUrlOpt;
			year <- yearOpt;
			projName <- projName
		) yield {
			val authorsStr = authors.map{p => s"${p.lastName}, ${p.firstName.head}., "}.mkString
			s"${authorsStr}$projName, $year. $title, $pidUrl"
		}

		new CitationInfo(pidUrlOpt, Option(authors).filterNot(_.isEmpty), titleOpt, yearOpt, tempCov, citText)
	}

	private def getSitesCitation(dobj: DataObject): CitationInfo = {
		val zoneId = ZoneId.of("UTC+01:00")
		val tempCov = getTemporalCoverageDisplay(dobj, zoneId)
		val titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					location <- acq.site.flatMap(_.location.flatMap(_.label));
					productionInstant <- productionTime(dobj);
					time <- tempCov
				) yield {
					val station = acq.station.org.name
					val year = formatDate(productionInstant, zoneId).take(4)
					val dataType = spec.split(",").head
					s"$station. $year. $dataType from $location, $time"
				}
		)
		val pidUrl = getPidUrl(dobj)
		val year = productionTime(dobj).map(productionInstant => formatDate(productionInstant, zoneId).take(4))
		val citText = for(
			title <- titleOpt
		) yield s"($year). $title. SITES Data Portal. $pidUrl"

		new CitationInfo(pidUrl, None, titleOpt, year, tempCov, citText)
	}

	private def getPidUrl(dobj: DataObject): Option[String] = for(
		pid <- dobj.doi.orElse(dobj.pid);
		handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic
	) yield s"$handleProxy$pid"

}

object CitationMaker{

	def getTemporalCoverageDisplay(dobj: DataObject, zoneId: ZoneId): Option[String] = dobj.specificInfo.fold(
		l3 => Some(getTimeFromInterval(l3.temporal.interval, zoneId)),
		l2 => l2.acquisition.interval.map(getTimeFromInterval(_, zoneId))
	)

	private def getTimeFromInterval(interval: TimeInterval, zoneId: ZoneId): String = {
		val duration = Duration.between(interval.start, interval.stop)
		val startZonedDateTime = ZonedDateTime.ofInstant(interval.start, zoneId)
		val stopZonedDateTime = ZonedDateTime.ofInstant(interval.stop, zoneId)
		if (duration.getSeconds < 24 * 3601) { //daily data object
			val middle = Instant.ofEpochMilli((interval.start.toEpochMilli + interval.stop.toEpochMilli) / 2)
			formatDate(middle, zoneId)
		} else if (startZonedDateTime.getDayOfYear == 1 && stopZonedDateTime.getDayOfYear == 1) {
			if (startZonedDateTime.getYear == stopZonedDateTime.getYear - 1) {
				s"${startZonedDateTime.getYear}"
			} else {
				s"${startZonedDateTime.getYear}–${stopZonedDateTime.getYear - 1}"
			}
		} else {
			val from = formatDate(interval.start, zoneId)
			val to = formatDate(interval.stop, zoneId)
			s"$from–$to"
		}
	}

	private def formatDate(inst: Instant, zoneId: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(zoneId).format(inst)

	private def productionTime(dobj: DataObject): Option[Instant] =
		dobj.production.map(_.dateTime).orElse{
			dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
		}

}
