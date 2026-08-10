package se.lu.nateko.cp.meta.upload.drought

import se.lu.nateko.cp.doi.Doi

import scala.concurrent.Future

/** Test/manual-upload seam. Production DOI citation retrieval is owned by rdfstore. */
trait DoiCitationLookup:
	def getHtmlCitation(doi: Doi): Future[String]

object DoiCitationLookup:
	val unavailable: DoiCitationLookup = _ => Future.failed(IllegalStateException("No local citation service in meta"))
