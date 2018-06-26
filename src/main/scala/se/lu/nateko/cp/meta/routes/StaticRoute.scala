package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.Html

import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import se.lu.nateko.cp.meta.OntoConfig
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling

object StaticRoute {

	private[this] val pages: PartialFunction[String, Html] = {
		case "labeling" => views.html.LabelingPage()
		case "sparqlclient" => views.html.SparqlClientPage()
		case "station" => views.html.StationPage()
	}

	private implicit val pageMarshaller = PageContentMarshalling.twirlHtmlMarshaller

	def apply(config: OntoConfig, authConf: PublicAuthConfig): Route = get{

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
		pathPrefix("uploadgui"){
			uploadGuiRoute(false, authConf)
		} ~
		pathPrefix("uploadguidev"){
			uploadGuiRoute(true, authConf)
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

	private def uploadGuiRoute(devVersion: Boolean, authConf: PublicAuthConfig): Route = {
		pathSingleSlash {
			complete(views.html.UploadGuiPage(devVersion, authConf))
		} ~
		path(Segment){res =>
			getFromResource(res)
		}
	}

}
