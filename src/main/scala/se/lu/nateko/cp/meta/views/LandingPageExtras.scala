package se.lu.nateko.cp.meta.views

final case class LandingPageExtras (
	downloadStats: Option[Int],
	previewStats: Option[Int],
	errors: Seq[String]
)
