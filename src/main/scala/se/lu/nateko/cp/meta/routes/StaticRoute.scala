package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.Html
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import se.lu.nateko.cp.meta.OntoConfig
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import scala.language.postfixOps

object StaticRoute {

	private[this] val pages: PartialFunction[(String, Envri), Html] = {
		case ("labeling", _) => views.html.LabelingPage()
		case ("sparqlclient", envri) => views.html.SparqlClientPage(envri)
		case ("station", envri) => views.html.StationPage(envri)
	}

	import PageContentMarshalling.twirlHtmlEntityMarshaller

	def apply(config: OntoConfig, authConf: PublicAuthConfig)(implicit evnrConfs: EnvriConfigs): Route = {

		val extractEnvri = AuthenticationRouting.extractEnvriDirective

		def uploadGuiRoute(pathPref: String, devVersion: Boolean): Route = (get & pathPrefix(pathPref)){
			extractEnvri{envri =>
				pathSingleSlash {
					complete(views.html.UploadGuiPage(devVersion, envri, authConf))
				} ~
				path(Segment){getFromResource}
			}
		}

		(get & pathPrefix("edit" / Segment)){ontId =>
			path("metaentry.js"){
				getFromResource("www/metaentry.js")
			} ~ {
				config.instOntoServers.get(ontId) match {
					case Some(ontConfig) => extractEnvri{envri =>
							pathSingleSlash{
								complete(views.html.MetaentryPage(ontConfig.serviceTitle, envri, authConf))
							}
						}
					case None =>
						complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
				}
			}
		} ~
		uploadGuiRoute("uploadgui", false) ~
		uploadGuiRoute("uploadguidev", true) ~
		path("uploadgui" / "gcmdkeywords.json"){
			getFromResource("gcmdkeywords.json")
		} ~
		(get & pathPrefix(Segment)){page =>
			extractEnvri{envri =>
				if(pages.isDefinedAt(page, envri)) {
					pathSingleSlash{
						complete(pages(page, envri))
					} ~
					pathEnd{
						redirect(s"/$page/", StatusCodes.Found)
					} ~
					path(s"$page.js"){
						getFromResource(s"www/$page.js")
					}
				} else reject
			}
		} ~
		(post & path("sparqlclient" /)){
			extractEnvri{envri =>
				formField("query"){query =>
					complete(views.html.SparqlClientPage(envri, Some(query)))
				} ~
				complete(StatusCodes.BadRequest -> "Expected 'query' form field with SPARQL query content")
			}
		}
	}
}
