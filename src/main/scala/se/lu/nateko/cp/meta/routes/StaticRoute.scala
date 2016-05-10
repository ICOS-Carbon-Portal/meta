package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.CpmetaConfig
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import java.net.URI
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerSerializer

object StaticRoute {

	private val staticPrefixes = Seq("labeling", "sparqlclient", "station").map(x => (x, x)).toMap

	def apply(config: CpmetaConfig, instanceServers: Map[String, InstanceServer]): Route = get{
		implicit val instServerMarshaller = InstanceServerSerializer.marshaller

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
		(pathPrefix("ontologies" | "resources") & extractUri){uri =>
			val path = uri.path.toString
			val serverOpt: Option[InstanceServer] = config.instanceServers.collectFirst{
				case (id, instServConf)
					if instServConf.writeContexts.exists(_.toString.endsWith(path)) =>
						instanceServers.get(id)
			}.flatten
			serverOpt match{
				case None => complete(StatusCodes.NotFound)
				case Some(instServer) => complete(instServer)
			}
		} ~
		pathPrefix(staticPrefixes){ prefix =>
			pathEnd{ redirect(s"/$prefix/", StatusCodes.MovedPermanently) } ~
			pathSingleSlash{ getFromResource(s"www/$prefix.html") } ~
			getFromResourceDirectory("www")
		}

	}

}