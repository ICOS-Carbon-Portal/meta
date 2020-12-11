package se.lu.nateko.cp.meta.services.citation

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.ZonedDateTime

import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.core.MetaCoreConfig

import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.Person

class CitationInfo(val citationString: String, val authors: Option[Seq[Person]], val tempCovDisplay: Option[String])

class CitationMaker(doiCiter: PlainDoiCiter, repo: Repository, coreConf: MetaCoreConfig) {
	import CitationMaker._
	private implicit val envriConfs = coreConf.envriConfigs

	val vocab = new CpVocab(repo.getValueFactory)
	private val attrProvider = new AttributionProvider(repo, vocab)

	def getCitationString(coll: StaticCollection) = getDoiCitation(coll.doi).map(_.citationString)

	def getCitationInfo(dobj: StaticObject)(implicit envri: Envri.Value): Option[CitationInfo] = {
		val getCitation = if (envri == Envri.SITES) getSitesCitation(_) else getIcosCitation(_)
		dobj.asDataObject.flatMap(getCitation).orElse(getDoiCitation(dobj.doi))
	}

	private def getDoiCitation(doiStrOpt: Option[String]): Option[CitationInfo] = for(
		doiStr <- doiStrOpt;
		doi <- Doi.unapply(doiStr);
		cit <- doiCiter.getCitationEager(doi)
	) yield new CitationInfo(cit, None, None)

	def getIcosCitation(dobj: DataObject): Option[CitationInfo] = {
		val zoneId = ZoneId.of("UTC")

		def titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					time <- getTemporalCoverageDisplay(dobj, zoneId)
				) yield {
					val station = acq.station.name
					val height = acq.samplingHeight.fold("")(sh => s" ($sh m)")
					s"$spec, $station$height, $time"
				}
		)
		for(
			title <- titleOpt;
			pid <- dobj.doi.orElse(dobj.pid);
			productionInstant <- productionTime(dobj)
		) yield {
			val authors = attrProvider.getAuthors(dobj)
			val tempCov = getTemporalCoverageDisplay(dobj, zoneId)

			if(dobj.specification.project.self.uri === vocab.icosProject) {
				val authorsStr = authors.map{p => s"${p.lastName}, ${p.firstName.head}., "}.mkString
				val handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic
				val year = formatDate(productionInstant, zoneId).take(4)
				new CitationInfo(s"${authorsStr}ICOS RI, $year. $title, ${handleProxy}$pid", Some(authors), tempCov)
			} else {
				new CitationInfo("", Some(authors), tempCov)
			}
		}
	}

	def getSitesCitation(dobj: DataObject): Option[CitationInfo] = {
		val zoneId = ZoneId.of("UTC+01:00")
		val titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					location <- acq.site.flatMap(_.location.flatMap(_.label));
					interval <- acq.interval;
					productionInstant = dobj.production.fold(interval.stop)(_.dateTime);
					time <- getTemporalCoverageDisplay(dobj, zoneId)
				) yield {
					val station = acq.station.name
					val year = formatDate(productionInstant, zoneId).take(4)
					val dataType = spec.split(",").head
					s"$station. $year. $dataType from $location, $time"
				}
		)

		for(
			title <- titleOpt;
			handleProxy = if(dobj.doi.isDefined) coreConf.handleProxies.doi else coreConf.handleProxies.basic;
			pid <- dobj.doi.orElse(dobj.pid)
		) yield {
			val tempCov = getTemporalCoverageDisplay(dobj, zoneId)
			new CitationInfo(s"$title. SITES Data Portal. ${handleProxy}$pid", None, tempCov)
		}
	}

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
