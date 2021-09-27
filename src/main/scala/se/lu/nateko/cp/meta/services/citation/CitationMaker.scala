package se.lu.nateko.cp.meta.services.citation

import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.icos.EtcMetaSource.toCETnoon
import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.parseCommaSepList
import se.lu.nateko.cp.meta.utils.rdf4j._

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import CitationStyle._

class CitationInfo(
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

	val server = new Rdf4jInstanceServer(repo)
	val vocab = new CpVocab(server.factory)
	protected val hasKeywords = (new CpmetaVocab(server.factory)).hasKeywords
	val attrProvider = new AttributionProvider(repo, vocab)

	def getItemCitationInfo(item: CitableItem) = References.empty.copy(
		citationString = getDoiCitation(item, CitationStyle.TEXT),
		citationBibTex = getDoiCitation(item, CitationStyle.BIBTEX),
		citationRis    = getDoiCitation(item, CitationStyle.RIS)
	)

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
				citationRis = getDoiCitation(data, CitationStyle.RIS).orElse(Some(structuredCitations.toRis)),
				authors = citInfo.authors,
				temporalCoverageDisplay = citInfo.tempCovDisplay,
				keywords = keywords,
				acknowledgements = Option(getFundingAcknowledgements(data)).filter(_.nonEmpty),
				licence = getLicence
			)

		case doc: DocObject => getItemCitationInfo(doc)
	}

	//will depend on other things in the future
	def getLicence(implicit envri: Envri): Option[Licence] = defaultLicences.get(envri)

	def presentDoiCitation(eagerRes: Option[Try[String]]): String = eagerRes match{
		case None => "Fetching... try [refreshing the page] again in a few seconds"
		case Some(Success(cit)) => cit
		case Some(Failure(err)) => "Error fetching DOI citation: " + err.getMessage
	}

	def extractDoiCitation(style: CitationStyle): PartialFunction[String, String] = {
		case Doi(doi) => presentDoiCitation(doiCiter.getCitationEager(doi, style))
	}

	private def getDoiCitation(item: CitableItem, style: CitationStyle): Option[String] =
		item.doi.collect{ extractDoiCitation(style) }

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
		val yearOpt = productionTime(dobj).map(getYear(zoneId))

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
		val yearOpt = dobj.submission.stop.map(getYear(zoneId))

		val titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					location <- acq.site.flatMap(_.location.flatMap(_.label));
					year <- yearOpt;
					time <- tempCov
				) yield {
					val station = acq.station.org.name
					val dataType = spec.split(",").head
					val samplingPoint = acq.samplingPoint.flatMap(_.label)
					s"$station ($year). $dataType from ${samplingPoint.getOrElse(location)}, $time"
				}
		)
		val pidUrlOpt = getPidUrl(dobj)
		val citString = for(
			title <- titleOpt;
			pidUrl <- pidUrlOpt
		) yield s"$title [Data set]. Swedish Infrastructure for Ecosystem Science (SITES). $pidUrl"
		new CitationInfo(pidUrlOpt, None, titleOpt, yearOpt, tempCov, citString)

	}

	private def getPidUrl(dobj: DataObject): Option[String] = for(
		pid <- dobj.doi.orElse(dobj.pid);
		handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic
	) yield s"$handleProxy$pid"

	private def getFundingAcknowledgements(dobj: DataObject): Seq[String] = dobj.specificInfo match{
		case Right(l2) =>
			val acq = l2.acquisition
			acq.station.funding.toSeq.flatten.filter{funding =>
				funding.start.fold(true){
					fstart => acq.interval.fold(true)(_.stop.compareTo(toCETnoon(fstart)) > 0)
				} &&
				funding.stop.fold(true){
					fstop => acq.interval.fold(true)(_.start.compareTo(toCETnoon(fstop)) < 0)
				}
			}.map{funding =>
				val grantTitle = List(funding.awardTitle, funding.awardNumber).flatten match{
					case only :: Nil => s" $only"
					case title :: number :: Nil => s" $title ($number)"
					case _ => ""
				}
				s"Work was funded by grant$grantTitle from ${funding.funder.org.name}"
			}
		case _ => Seq.empty
	}
}

object CitationMaker{

	val defaultLicences: Map[Envri, Licence] = Map(
		Envri.ICOS -> Licence("ICOS CCBY4 Data Licence", new URI("https://data.icos-cp.eu/licence")),
		Envri.SITES -> Licence("SITES CCBY4 Data Licence",new URI("https://data.fieldsites.se/licence"))
	)

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
		} else if (isMidnight(startZonedDateTime) && isMidnight(stopZonedDateTime)) {
			val from = formatDate(interval.start, zoneId)
			val to = formatDate(interval.stop.minus(1, ChronoUnit.DAYS), zoneId)
			s"$from–$to"
		} else {
			val from = formatDate(interval.start, zoneId)
			val to = formatDate(interval.stop, zoneId)
			s"$from–$to"
		}
	}

	private def getYear(zoneId: ZoneId)(prodInst: Instant): String = formatDate(prodInst, zoneId).take(4)

	private def formatDate(inst: Instant, zoneId: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(zoneId).format(inst)

	private def productionTime(dobj: DataObject): Option[Instant] =
		dobj.production.map(_.dateTime).orElse{
			dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
		}

	private def isMidnight(dateTime: ZonedDateTime): Boolean = dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME) == "00:00:00"

}
