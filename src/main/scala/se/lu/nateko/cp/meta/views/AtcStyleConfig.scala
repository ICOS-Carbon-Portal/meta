package se.lu.nateko.cp.meta.views

import se.lu.nateko.cp.viewscore.IcosStyleConfig
import java.net.URI

object AtcStyleConfig extends IcosStyleConfig{
	private val prefix = "https://static.icos-cp.eu/images/atc/"

	val headerImage = new URI(prefix + "icos-atc_header.jpg")
	val headerImageMedium = new URI(prefix + "icos-atc_header_medium.jpg")
	val headerImageSmall = new URI(prefix + "icos-atc_header_small.jpg")
	val headerLogo = new URI(prefix + "atc_logo_opt.svg")
	val headerHomeLink = new URI("http://www.icos-atc.eu/")
}
