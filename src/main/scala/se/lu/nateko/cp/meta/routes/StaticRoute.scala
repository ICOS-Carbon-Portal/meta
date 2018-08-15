package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.Html
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import se.lu.nateko.cp.meta.OntoConfig
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling

object StaticRoute {

	private[this] val pages: PartialFunction[String, Html] = {
		case "labeling" => views.html.LabelingPage()
		case "sparqlclient" => views.html.SparqlClientPage()
		case "station" => views.html.StationPage()
	}

	private implicit val pageMarshaller = PageContentMarshalling.twirlHtmlMarshaller

	def apply(config: OntoConfig, authConf: PublicAuthConfig)(implicit evnrConfs: EnvriConfigs): Route = get{

		val extractEnvri = extractEnvriDirective

		pathPrefix("edit" / Segment){ontId =>
			path("metaentry.js"){
				getFromResource("www/metaentry.js")
			} ~ {
				config.instOntoServers.get(ontId) match {
					case Some(ontConfig) => pathSingleSlash{
							complete(views.html.MetaentryPage(ontConfig.serviceTitle, authConf))
						}
					case None =>
						complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
				}
			}
		} ~
		extractEnvri{envri =>

			def uploadGuiRoute(devVersion: Boolean): Route =
				pathSingleSlash {
					complete(views.html.UploadGuiPage(devVersion, envri, authConf))
				} ~
				path(Segment){getFromResource}

			pathPrefix("uploadgui"){uploadGuiRoute(false)} ~
			pathPrefix("uploadguidev"){uploadGuiRoute(true)}
		} ~
		pathPrefix(Segment){page =>
			if(pages.isDefinedAt(page)) {
				pathSingleSlash{
					complete(pages(page))
				} ~
				pathEnd{
					redirect(s"/$page/", StatusCodes.Found)
				} ~
				path(s"$page.js"){
					getFromResource(s"www/$page.js")
				}
			} else reject
		}
	}

	def extractEnvriDirective(implicit configs: EnvriConfigs): Directive1[Envri] = extractHost.flatMap{h =>
		Envri.infer(h) match{
			case None => complete(StatusCodes.BadRequest -> s"Unexpected host $h, cannot find corresponding ENVRI")
			case Some(envri) => provide(envri)
		}
	}
}
