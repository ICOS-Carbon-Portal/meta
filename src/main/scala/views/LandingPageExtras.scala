package se.lu.nateko.cp.meta.views

import se.lu.nateko.cp.meta.services.citation.RemoteCitation

case class LandingPageExtras (
	downloadStats: Option[Int],
	previewStats: Option[Int],
	errors: Seq[String],
	externalHostLabel: Option[String] = None,
	remoteCitation: RemoteCitation = RemoteCitation.NotApplicable
)
