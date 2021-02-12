package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.services.ExportService

object SitemapRoute {
	def apply(sparqler: SparqlRunner)(implicit configs: EnvriConfigs): Route = {
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		(get & path("sitemap.xml") & extractEnvri){ implicit envri =>
			complete(
				HttpEntity(
					ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
					views.xml.Sitemap(ExportService.dataObjs(sparqler)).body
				)
			)
		}
	}
}