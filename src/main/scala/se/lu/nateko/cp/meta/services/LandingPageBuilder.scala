package se.lu.nateko.cp.meta.services

import org.apache.commons.io.IOUtils
import se.lu.nateko.cp.meta.core.data.DataPackage

object LandingPageBuilder {

	def getPage(dataPackage: DataPackage): String = {

		var htmlDoc = IOUtils.toString(getClass.getResourceAsStream("/htmltemplates/landing_page.html"), "UTF-8")

		htmlDoc = htmlDoc.replaceAllLiterally("$producerName", dataPackage.production.producer.label.getOrElse(
			dataPackage.production.producer.uri.toString
		))
		htmlDoc = htmlDoc.replaceAllLiterally("$base64UrlPackageId", dataPackage.hash.base64Url)
		htmlDoc = htmlDoc.replaceAllLiterally("$hexHash", dataPackage.hash.hex)

		htmlDoc = htmlDoc.replaceAllLiterally("$debug", dataPackage.toString)

		htmlDoc
	}

}
