package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._

object StaticRoute {

	def apply(config: CpmetaConfig): Route = get{
		pathPrefix("edit" / Segment){ontId =>
			path("metaentry.js"){
				getFromResource("www/metaentry.js")
			} ~ {
				if(config.onto.instOntoServers.contains(ontId)){
					pathSingleSlash{
						getFromResource("www/metaentry.html")
					}
				} else
					complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
			}
		} ~
		path("ontologies" / Segment){ ontId =>
			config.onto.instOntoServers.get(ontId) match{
				case None =>
					complete(StatusCodes.NotFound)
				case Some(ontConf) =>
					val ontId = ontConf.ontoId
					val ontRes = config.onto.ontologies.find(_.ontoId.contains(ontId)).map(_.owlResource)
					ontRes match{
						case None =>
							complete(StatusCodes.NotFound)
						case Some(owlResource) => getFromResource(owlResource.stripPrefix("/"))
					}
			}
		} ~
		path("labeling" / "labeling.js"){
			getFromResource("www/labeling.js")
		}
		
	}

}