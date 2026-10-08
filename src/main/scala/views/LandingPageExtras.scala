package se.lu.nateko.cp.meta.views

case class LandingPageExtras (
	downloadStats: Option[Int],
	previewStats: Option[Int],
	errors: Seq[String],
	externalHostLabel: Option[String] = None,
	citationPending: Boolean = false
)
