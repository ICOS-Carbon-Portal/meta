package se.lu.nateko.cp.meta.upload.drought

import se.lu.nateko.cp.doi.Doi

import scala.concurrent.Future

trait DoiCitationLookup:
	def getHtmlCitation(doi: Doi): Future[String]
