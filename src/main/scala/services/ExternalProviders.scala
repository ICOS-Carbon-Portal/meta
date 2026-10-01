package se.lu.nateko.cp.meta.services

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, StaticObject, staticObjAccessUrl}
import se.lu.nateko.cp.meta.{ExternalPidPolicy, ExternalProviderConfig}

import java.net.URI

class ExternalProviders(conf: Map[Envri, Seq[ExternalProviderConfig]]):

	def lookup(landingPage: URI)(using envri: Envri): Option[ExternalProviderConfig] =
		conf.getOrElse(envri, Nil).find(_.host == landingPage.getHost)

	def mintsLocalPid(landingPage: Option[URI])(using Envri): Boolean =
		landingPage.flatMap(lookup).forall(_.pid == ExternalPidPolicy.MINT_LOCAL)

object ExternalProviders:

	def externalLandingPage(obj: StaticObject)(using EnvriConfig): Option[URI] =
		obj.accessUrl.filterNot(_ == staticObjAccessUrl(obj.hash))
