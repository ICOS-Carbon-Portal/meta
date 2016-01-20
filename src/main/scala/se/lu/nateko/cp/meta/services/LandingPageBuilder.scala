package se.lu.nateko.cp.meta.services

import org.apache.commons.io.IOUtils
import se.lu.nateko.cp.meta.core.data.DataPackage
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import spray.json._

object LandingPageBuilder {

	def getPage(dataPackage: DataPackage): String = {

		val producer = dataPackage.production.producer

		IOUtils.toString(getClass.getResourceAsStream("/htmltemplates/landing_page.html"), "UTF-8")
			.replaceAllLiterally("$producerName", producer.label.getOrElse(producer.uri.toString))
			.replaceAllLiterally("$base64UrlPackageId", dataPackage.hash.base64Url)
			.replaceAllLiterally("$hexHash", dataPackage.hash.hex)
			.replaceAllLiterally("$debug", dataPackage.toJson.prettyPrint)

	}

}
