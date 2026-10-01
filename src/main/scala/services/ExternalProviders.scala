package se.lu.nateko.cp.meta.services

import se.lu.nateko.cp.meta.core.data.{EnvriConfig, StaticObject, staticObjAccessUrl}

import java.net.URI

object ExternalProviders:

	def externalLandingPage(obj: StaticObject)(using EnvriConfig): Option[URI] =
		obj.accessUrl.filterNot(_ == staticObjAccessUrl(obj.hash))
