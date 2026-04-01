package se.lu.nateko.cp.meta.routes

import scala.language.unsafeNulls

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.PathMatcher1
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.data.CountryCode
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.envriConf
import se.lu.nateko.cp.meta.services.metaexport.SchemaOrg

import java.net.URI


object SitemapRoute:

	val SiteMap = "sitemap.xml"
	val GlobalDataSitemap = "data-sitemap.xml"
	val CollectionsSitemap = "collections-sitemap.xml"
	val DocumentsSitemap = "documents-sitemap.xml"

	val DataSiteMap: PathMatcher1[Option[CountryCode]] =
		val ccStr: PathMatcher1[String] = "([a-zA-Z]{2})-".r
		(ccStr.? ~ GlobalDataSitemap).flatMap:
			case Some(ccTxt) => ccTxt.toUpperCase match
				case CountryCode(cc) => Some(Some(cc))
				case _ => None
			case None => Some(None)

	def apply(sparqler: SparqlRunner)(using EnvriConfigs): Route =

		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		def renderSitemap(schemaObjects: SparqlRunner => Seq[URI]) = complete:
			HttpEntity(
				ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
				views.xml.Sitemap(schemaObjects(sparqler)).body
			)


		(get & extractEnvri){ implicit envri =>
			given econf: EnvriConfig = envriConf
			path(SiteMap):
				val sitemapsUris = Seq(GlobalDataSitemap, CollectionsSitemap, DocumentsSitemap).map:
					fname => URI(s"https://${econf.metaHost}/$fname")

				complete(
					HttpEntity(
						ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
						views.xml.IndexSitemap(sitemapsUris).body
					)
				)
			~
			path(DataSiteMap): countryCodeOpt =>
				renderSitemap(SchemaOrg.dataObjs(_, countryCodeOpt))
			~
			path(CollectionsSitemap):
				renderSitemap(SchemaOrg.collObjs)
			~
			path(DocumentsSitemap):
				renderSitemap(SchemaOrg.docObjs)
		}
	end apply

end SitemapRoute
