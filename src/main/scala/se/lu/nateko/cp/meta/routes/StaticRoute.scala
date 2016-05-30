package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.OntoConfig
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._

object StaticRoute {

	private val staticPrefixes = Seq("labeling", "sparqlclient", "station").map(x => (x, x)).toMap

	def apply(config: OntoConfig): Route = get{

		pathPrefix("edit" / Segment){ontId =>
			path("metaentry.js"){
				getFromResource("www/metaentry.js")
			} ~ {
				if(config.instOntoServers.contains(ontId)){
					pathSingleSlash{
						getFromResource("www/metaentry.html")
					}
				} else
					complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
			}
		} ~
		pathPrefix(staticPrefixes){ prefix =>
			pathEnd{ redirect(s"/$prefix/", StatusCodes.MovedPermanently) } ~
			pathSingleSlash{ getFromResource(s"www/$prefix.html") } ~
			getFromResourceDirectory("www")
		}

	}

}