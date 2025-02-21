package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs, envriConf}
import java.net.URI
import se.lu.nateko.cp.meta.services.metaexport.SchemaOrg
import se.lu.nateko.cp.meta.core.data.CountryCode

object SitemapRoute {

	val Data = "data"
	val Collections = "collections"
	val Documents = "documents"

	private val exceptionHandler = ExceptionHandler{

		case argErr: IllegalArgumentException =>
			complete(StatusCodes.NotFound)

		case err => throw err
	}

	def apply(sparqler: SparqlRunner)(using EnvriConfigs): Route = {
		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		(get & extractEnvri){ implicit envri =>
			given EnvriConfig = envriConf
			path("sitemap.xml"):
				val sitemaps = Seq(
					URI(s"${envriConf.metaItemPrefix}${Data}-sitemap.xml"),
					URI(s"${envriConf.metaItemPrefix}${Collections}-sitemap.xml"),
					URI(s"${envriConf.metaItemPrefix}${Documents}-sitemap.xml"))

				complete(
					HttpEntity(
						ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
						views.xml.IndexSitemap(sitemaps).body
					)
				)
			~
			path("""([a-z]{2})-data-sitemap.xml""".r):
				case countryCode if CountryCode.unapply(countryCode.toUpperCase).isDefined =>
					handleExceptions(exceptionHandler){completeRequest(SchemaOrg.dataObjsByCountry(sparqler, countryCode.toUpperCase))}
				case _ => reject
			~
			path("""([a-z]{4,11})-sitemap.xml""".r):
				case Data => completeRequest(SchemaOrg.dataObjs(sparqler))
				case Collections => completeRequest(SchemaOrg.collObjs(sparqler))
				case Documents => completeRequest(SchemaOrg.docObjs(sparqler))
				case _ => reject
		}
	}

	private def completeRequest(schemaObjects: => Seq[URI]) (using EnvriConfig) =
		complete(
			HttpEntity(
				ContentType.WithCharset(MediaTypes.`text/xml`, HttpCharsets.`UTF-8`),
				views.xml.Sitemap(schemaObjects).body
			)
		)
}
