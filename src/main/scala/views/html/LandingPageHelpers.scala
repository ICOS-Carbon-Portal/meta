package views.html

import se.lu.nateko.cp.meta.core.data._
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import spray.json._


object LandingPageHelpers{
	def printToJson(dataObj: DataObject): String = dataObj.toJson.prettyPrint

	private def formatDate(inst: Instant): String = {
		val formatter: DateTimeFormatter = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.of("UTC"))

		formatter.format(inst)
	}

	implicit class PresentableInstant(val inst: Instant) extends AnyVal{
		def getDateTimeStr: String = formatDate(inst)
	}

	implicit class OptionalInstant(val inst: Option[Instant]) extends AnyVal{
		def getDateTimeStr: String = {
			inst match {
				case Some(i) => formatDate(i)
				case None => "Not done"
			}
		}
	}

}