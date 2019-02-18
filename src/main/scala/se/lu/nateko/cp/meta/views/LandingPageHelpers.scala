package se.lu.nateko.cp.meta.views

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.rdf4j._
import spray.json._
import java.time.temporal.ChronoField


object LandingPageHelpers{

	def printToJson(dataObj: DataObject): String = dataObj.toJson.prettyPrint

	private def formatDateTime(inst: Instant): String = {
		val formatter: DateTimeFormatter = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.of("UTC"))

		formatter.format(inst)
	}

	private def formatDate(inst: Instant): String = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.of("UTC")).format(inst)

	implicit class PresentableInstant(val inst: Instant) extends AnyVal{
		def getDateTimeStr: String = formatDateTime(inst)
	}

	implicit class OptionalInstant(val inst: Option[Instant]) extends AnyVal{
		def getDateTimeStr: String = {
			inst match {
				case Some(i) => formatDateTime(i)
				case None => "Not done"
			}
		}
	}

	def icosCitation(dobj: DataObject, vocab: CpVocab, handleService: URI): Option[String] = {
		val isIcos: Option[Unit] = if(dobj.specification.project.uri === vocab.icosProject) Some(()) else None

		def titleOpt = dobj.specificInfo.fold(
			l3 => Some(l3.title),
			l2 => for(
					spec <- dobj.specification.self.label;
					acq = l2.acquisition;
					interval <- acq.interval
				) yield {
					val station = acq.station.name
					val height = acq.samplingHeight.fold("")(sh => s" ($sh m)")
					val duration = Duration.between(interval.start, interval.stop)
					val time = if(duration.getSeconds < 24 * 3601){ //daily data object
						val middle = Instant.ofEpochMilli((interval.start.toEpochMilli + interval.stop.toEpochMilli) / 2)
						formatDate(middle)
					} else{
						val from = formatDate(interval.start)
						val to = formatDate(interval.stop)
						s"$from-$to"
					}
					s"$spec, $station$height, $time"
				}
		)
		val productionInstantOpt = dobj.production.map(_.dateTime).orElse{
			dobj.specificInfo.toOption.flatMap(_.acquisition.interval).map(_.stop)
		}
		for(
			_ <- isIcos;
			title <- titleOpt;
			pid <- dobj.doi.orElse(dobj.pid);
			instant <- productionInstantOpt
		) yield {

			val station = dobj.specificInfo.fold(
				_ => None, //L3 data
				l2 => if(dobj.specification.dataLevel > 1) None else{
					Some(l2.acquisition.station.name)
				}
			).fold("")(_ + ", ")

			val producerOrg = dobj.production.flatMap(_.creator match{
				case Organization(_, name) => Some(name)
				case _ => None
			}).fold("")(_ + ", ")

			val icos = if(dobj.specification.dataLevel == 2) "ICOS ERIC, " else ""

			val authors = s"$icos$producerOrg$station"
			val year = formatDate(instant).take(4)
			s"$authors$title, $handleService$pid, $year"
		}
	}
}