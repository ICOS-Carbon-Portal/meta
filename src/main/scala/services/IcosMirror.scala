package se.lu.nateko.cp.meta.services

import eu.icoscp.envri.Envri

import java.net.URI

object IcosMirror:

	val IcosMetaHost = "meta.icos-cp.eu"

	def isIcosMirrored(accessUrl: Option[URI])(using envri: Envri): Boolean =
		envri == Envri.SITES && accessUrl.exists(IcosMetaHost == _.getHost)
