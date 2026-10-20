package se.lu.nateko.cp.meta.services.citation

import se.lu.nateko.cp.doi.Doi

import scala.concurrent.Future

trait CitationClient:
	def getCitation(doi: Doi, citationStyle: CitationStyle): Future[String]
