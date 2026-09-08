package se.lu.nateko.cp.meta.services.metadata

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime, ZoneId, ZoneOffset, ZonedDateTime}

/** Metadata formatting and selection rules that do not require DOI/citation I/O. */
object StaticMetadata:
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

	def getFundingObjects(dobj: DataObject): Seq[Funding] = dobj.specificInfo match
		case Right(stationTs) =>
			val acq = stationTs.acquisition
			acq.station.funding.toSeq.flatten.filter: funding =>
				funding.start.fold(true)(fstart => acq.interval.fold(true)(_.stop.compareTo(toCETnoon(fstart)) > 0)) &&
				funding.stop.fold(true)(fstop => acq.interval.fold(true)(_.start.compareTo(toCETnoon(fstop)) < 0))
		case _ => Seq.empty

	def getTemporalCoverageDisplay(dobj: DataObject, zoneId: ZoneId): Option[String] = dobj.specificInfo.fold(
		spatioTemp => Some(getTimeFromInterval(spatioTemp.temporal.interval, zoneId)),
		stationTs => stationTs.acquisition.interval.map(getTimeFromInterval(_, zoneId))
	)

	private def toCETnoon(date: LocalDate): Instant =
		LocalDateTime.of(date, LocalTime.NOON).atOffset(ZoneOffset.ofHours(1)).toInstant

	private def getTimeFromInterval(interval: TimeInterval, zoneId: ZoneId): String =
		val duration = Duration.between(interval.start, interval.stop)
		val start = ZonedDateTime.ofInstant(interval.start, zoneId)
		val stop = ZonedDateTime.ofInstant(interval.stop, zoneId)
		if duration.getSeconds < 24 * 3601 then formatDate(Instant.ofEpochMilli((interval.start.toEpochMilli + interval.stop.toEpochMilli) / 2), zoneId)
		else if start.getDayOfYear == 1 && stop.getDayOfYear == 1 then
			if start.getYear == stop.getYear - 1 then s"${start.getYear}" else s"${start.getYear}–${stop.getYear - 1}"
		else if isMidnight(start) && isMidnight(stop) then
			s"${formatDate(interval.start, zoneId)}–${formatDate(interval.stop.minus(1, ChronoUnit.DAYS), zoneId)}"
		else s"${formatDate(interval.start, zoneId)}–${formatDate(interval.stop, zoneId)}"

	private def formatDate(inst: Instant, zoneId: ZoneId): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(zoneId).format(inst)
	private def isMidnight(dateTime: ZonedDateTime): Boolean = dateTime.format(DateTimeFormatter.ISO_LOCAL_TIME) == "00:00:00"
