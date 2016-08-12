package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import play.twirl.api.Html

import se.lu.nateko.cp.meta.OntoConfig
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling

object StaticRoute {

	private[this] val pages: PartialFunction[String, Html] = {
		case "labeling" => views.html.LabelingPage()
		case "sparqlclient" => views.html.SparqlClientPage()
		case "station" => views.html.StationPage()
	}

	private implicit val pageMarshaller = PageContentMarshalling.twirlHtmlMarshaller

	def apply(config: OntoConfig): Route = get{

		pathPrefix("edit" / Segment){ontId =>
			path("metaentry.js"){
				getFromResource("www/metaentry.js")
			} ~ {
				if(config.instOntoServers.contains(ontId)){
					pathSingleSlash{
						complete(views.html.MetaentryPage())
					}
				} else
					complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
			}
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

}
