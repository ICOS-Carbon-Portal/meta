package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.metaflow.icos.EtcMetaSource.toCETnoon
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, ZoneId, ZonedDateTime}

/**
 * Pure, structural citation helpers that stay in the meta service.
 *
 * These derive display/funding/licence information from an object's own
 * structure and need neither DataCite access nor the triplestore, so meta keeps
 * using them directly (in upload validation, DataCite/Schema.org export, etc.).
 * The DataCite-backed citation assembly (the former `class CitationMaker`,
 * now `LiveCitationMaker`) lives in the standalone citations service.
 *
 * `getYear` and `productionTime` are `private[citation]` so that
 * `LiveCitationMaker` (same package, citations module) can reuse them.
 */
object CitationMaker:

	def defaultLicence(using envri: Envri): Licence = envri match
		case Envri.ICOS | Envri.ICOSCities => Licence(
			new URI(CpmetaVocab.MetaPrefix + "icosLicence"),
			"ICOS CCBY4 Data Licence",
			new URI("https://data.icos-cp.eu/licence"),
			Some(CpVocab.CCBY4)
		)
		case Envri.SITES => Licence(
			new URI(CpmetaVocab.SitesPrefix + "sitesLicence"),
			"SITES CCBY4 Data Licence",
			new URI("https://data.fieldsites.se/licence"),
			Some(CpVocab.CCBY4)
		)


	def getFundingObjects(dobj: DataObject): Seq[Funding] =  dobj.specificInfo match
		case Right(stationTs) =>
			val acq = stationTs.acquisition
			acq.station.funding.toSeq.flatten.filter{funding =>
				funding.start.fold(true){
					fstart => acq.interval.fold(true)(_.stop.compareTo(toCETnoon(fstart)) > 0)
				} &&
				funding.stop.fold(true){
					fstop => acq.interval.fold(true)(_.start.compareTo(toCETnoon(fstop)) < 0)
				}
			}
		case _ => Seq.empty

	def getTemporalCoverageDisplay(dobj: DataObject, zoneId: ZoneId): Option[String] = dobj.specificInfo.fold(
		spatioTemp => Some(getTimeFromInterval(spatioTemp.temporal.interval, zoneId)),
		stationTs => stationTs.acquisition.interval.map(getTimeFromInterval(_, zoneId))
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

	private[citation] def getYear(zoneId: ZoneId)(prodInst: Instant): String = formatDate(prodInst, zoneId).take(4)

	private def formatDate(inst: Instant, zoneId: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(zoneId).format(inst)

	private[citation] def productionTime(dobj: DataObject): Option[Instant] =
		dobj.production.map(_.dateTime).orElse{
			dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
		}

	private def isMidnight(dateTime: ZonedDateTime): Boolean = dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME) == "00:00:00"

end CitationMaker
