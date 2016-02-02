package views.html

import se.lu.nateko.cp.meta.core.data._
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import spray.json._


private object LandingPageHelpers{
	def printToJson(dataObj: DataObject): String = dataObj.toJson.prettyPrint

	implicit class PresentableResource(val res: UriResource) extends AnyVal{
		def getLabel: String = {
			res.label match {
				case Some(l) => l.capitalize
				case None => res.uri.toString
			}
		}
	}

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

	implicit class PresentableProducerTheme(val theme: ProducerTheme) extends AnyVal{
		def getValue: String = theme.toJson.convertTo[String]
	}

}