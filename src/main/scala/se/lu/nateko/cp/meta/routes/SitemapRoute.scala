package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{Route}
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs, envriConf}
import java.net.URI
import se.lu.nateko.cp.meta.services.metaexport.SchemaOrg
import se.lu.nateko.cp.meta.core.data.CountryCode

object SitemapRoute {

	val SiteMap = "sitemap.xml"
	val DataSitemap = "data-sitemap.xml"
	val CollectionsSitemap = "collections-sitemap.xml"
	val DocumentsSitemap = "documents-sitemap.xml"

	def apply(sparqler: SparqlRunner)(using EnvriConfigs): Route = {
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		(get & extractEnvri){ implicit envri =>
			given EnvriConfig = envriConf
			path(SiteMap):
				val sitemaps = Seq(
					URI(s"https://${envriConf.metaHost}/${DataSitemap}"),
					URI(s"https://${envriConf.metaHost}/${CollectionsSitemap}"),
					URI(s"https://${envriConf.metaHost}/${DocumentsSitemap}"))

				complete(
					HttpEntity(
						ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
						views.xml.IndexSitemap(sitemaps).body
					)
				)
			~
			path("""([a-zA-Z]{2})-data-sitemap\.xml""".r):
				case CountryCode(countryCode) =>
					completeSitemapRequest(SchemaOrg.dataObjsByCountry(sparqler, countryCode))
				case _ => reject
			~
			path(DataSitemap):
				completeSitemapRequest(SchemaOrg.dataObjs(sparqler))
			~
			path(CollectionsSitemap):
				completeSitemapRequest(SchemaOrg.collObjs(sparqler))
			~
			path(DocumentsSitemap):
				completeSitemapRequest(SchemaOrg.docObjs(sparqler))
		}
	}

	private def completeSitemapRequest(schemaObjects: => Seq[URI]) (using EnvriConfig) =
		complete(
			HttpEntity(
				ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
				views.xml.Sitemap(schemaObjects).body
			)
		)
}
