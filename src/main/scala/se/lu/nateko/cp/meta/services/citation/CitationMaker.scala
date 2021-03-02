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

private class CitationInfo(val citationString: Option[String], val authors: Option[Seq[Person]], val tempCovDisplay: Option[String])

class CitationMaker(doiCiter: PlainDoiCiter, repo: Repository, coreConf: MetaCoreConfig) extends FetchingHelper {
	import CitationMaker._
	private implicit val envriConfs = coreConf.envriConfigs

	protected val server = new Rdf4jInstanceServer(repo)
	val vocab = new CpVocab(server.factory)
	private val hasKeywords = (new CpmetaVocab(server.factory)).hasKeywords
	val attrProvider = new AttributionProvider(repo, vocab)

	def getCitationString(coll: StaticCollection): Option[String] = getDoiCitation(coll)

	def getCitationInfo(sobj: StaticObject)(implicit envri: Envri.Value): References = sobj match{
		case data: DataObject =>
			val citInfo = if (envri == Envri.SITES) getSitesCitation(data) else getIcosCitation(data)
			val dobj = vocab.getStaticObject(data.hash)
			References(
				citationString = getDoiCitation(data).orElse(citInfo.citationString),
				authors = citInfo.authors,
				temporalCoverageDisplay = citInfo.tempCovDisplay,
				keywords = getOptionalString(dobj, hasKeywords).map(s => parseCommaSepList(s).toIndexedSeq),
			)
		case doc: DocObject =>
			References.empty.copy(citationString = getDoiCitation(doc))
	}

	private def getDoiCitation(item: CitableItem): Option[String] = for(
		doiStr <- item.doi;
		doi <- Doi.unapply(doiStr);
		cit <- doiCiter.getCitationEager(doi)
	) yield cit

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
			}.sorted
		}

		val citString = for(
			title <- titleOpt;
			pidUrl <- getPidUrl(dobj);
			productionInstant <- productionTime(dobj);
			projName <- if(isIcosProject) Some("ICOS RI") else dobj.specification.project.self.label
		) yield {
			val authorsStr = authors.map{p => s"${p.lastName}, ${p.firstName.head}., "}.mkString
			val year = formatDate(productionInstant, zoneId).take(4)
			s"${authorsStr}$projName, $year. $title, $pidUrl"
		}
		new CitationInfo(citString, Some(authors), tempCov)
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
					publicationInstant <- dobj.submission.stop;
					time <- tempCov
				) yield {
					val station = acq.station.org.name
					val year = formatDate(publicationInstant, zoneId).take(4)
					val dataType = spec.split(",").head
					s"$station ($year). $dataType from $location, $time"
				}
		)

		val citString = for(
			title <- titleOpt;
			pidUrl <- getPidUrl(dobj)
		) yield s"$title [Data set]. Swedish Infrastructure for Ecosystem Science (SITES). $pidUrl"
		new CitationInfo(citString, None, tempCov)
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
