package se.lu.nateko.cp.meta.services.upload

import java.net.URI
import java.time.{ZoneId, Instant}
import java.time.format.DateTimeFormatter
import org.apache.commons.io.IOUtils
import se.lu.nateko.cp.meta.core.data.{UriResource, DataObject}
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import spray.json._

import se.lu.nateko.cp.meta.services.upload.html.LandingPage

object LandingPageBuilder {

	def getNewPage(dataPackage: DataObject): String = {
		LandingPage(dataPackage).body
	}

	def getPage(dataPackage: DataObject): String = {

		val producer = dataPackage.production.producer
		val submitter = dataPackage.submission.submitter

		IOUtils.toString(getClass.getResourceAsStream("/htmltemplates/landing_page.html"), "UTF-8")
			.replaceAllLiterally("$producerName", producer.getLabel)
			.replaceAllLiterally("$dataLevel", dataPackage.specification.dataLevel.toString)
			.replaceAllLiterally("$prodStart", formatDate(dataPackage.production.start))
			.replaceAllLiterally("$prodStop", formatDate(dataPackage.production.stop))

			.replaceAllLiterally("$submitterName", submitter.getLabel)
			.replaceAllLiterally("$start", formatDate(dataPackage.submission.start))
			.replaceAllLiterally("$stop", dataPackage.submission.stop.getDateTimeStr)

			.replaceAllLiterally("$downloadLnk", getAnchor(dataPackage.accessUrl))
			.replaceAllLiterally("$base64UrlPackageId", dataPackage.hash.base64Url)
			.replaceAllLiterally("$hexHash", dataPackage.hash.hex)
			.replaceAllLiterally("$specFormat", dataPackage.specification.format.getLabel)
			.replaceAllLiterally("$specEncoding", dataPackage.specification.encoding.getLabel)

			.replaceAllLiterally("$debug", dataPackage.toJson.prettyPrint)

	}

	private def formatDate(inst: Instant): String = {
		val formatter: DateTimeFormatter = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.of("UTC"))

		formatter.format(inst)
	}

	private def getAnchor(uri: URI): String = {
		val link = uri.toString
		val linkName = uri.getPath.split("/").last

		s"<a href='$link' target='_blank'>$linkName</a>"
	}

	implicit private class PresentableResource(val res: UriResource) extends AnyVal{
		def getLabel: String = {
			res.label match {
				case Some(l) => l.capitalize
				case None => res.uri.toString
			}
		}
	}

	implicit private class OptionalInstant(val inst: Option[Instant]) extends AnyVal{
		def getDateTimeStr: String = {
			inst match {
				case Some(i) => formatDate(i)
				case None => "Not done"
			}
		}
	}
}
