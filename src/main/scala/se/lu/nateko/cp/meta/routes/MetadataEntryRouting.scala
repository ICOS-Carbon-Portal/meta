package se.lu.nateko.cp.meta.routes

import se.lu.nateko.cp.meta.InstOnto
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.UpdateDto
import se.lu.nateko.cp.meta.ReplaceDto
import akka.stream.Materializer
import java.net.URI
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import se.lu.nateko.cp.meta.InstOntoServerConfig

class MetadataEntryRouting(authRouting: AuthenticationRouting)(implicit mat: Materializer) extends CpmetaJsonProtocol{

	def entryRoute(instOntos: Map[String, InstOnto], ontoConfs: Map[String, InstOntoServerConfig]): Route = {
		val ontoInfos = ontoConfs.map{
			case (ontId, conf) => (ontId, (instOntos(ontId), conf))
		}
		entryRoute(ontoInfos)
	}

	def entryRoute(ontoInfos: Map[String, (InstOnto, InstOntoServerConfig)]): Route =
		pathPrefix("edit" / Segment){ ontId =>
			ontoInfos.get(ontId) match{
				case None =>
					complete((StatusCodes.NotFound, s"Unrecognized metadata entry project: $ontId"))
				case Some((onto, ontConf)) =>
					singleOntoRoute(onto, ontConf.authorizedUserIds)
			}
		}

	def singleOntoRoute(instOnto: InstOnto, authorizedUsers: Seq[String]): Route = {
		val onto = instOnto.onto
		get{
			pathSuffix("getExposedClasses"){
				complete(onto.getExposedClasses)
			} ~
			pathSuffix("getTopLevelClasses"){
				complete(onto.getTopLevelClasses)
			} ~
			pathSuffix("listIndividuals"){
				parameter('classUri){ uriStr =>
					complete(instOnto.getIndividuals(new URI(uriStr)))
				}
			} ~
			pathSuffix("getIndividual"){
				parameter('uri){ uriStr =>
					complete(instOnto.getIndividual(new URI(uriStr)))
				}
			}
		} ~
		post{
			authRouting.allowUsers(authorizedUsers){
				pathSuffix("applyupdates"){
					entity(as[Seq[UpdateDto]])(updates => {
						instOnto.applyUpdates(updates).get
						complete(StatusCodes.OK)
					})
				}
				pathSuffix("performreplacement"){
					entity(as[ReplaceDto])(replacement => {
						instOnto.performReplacement(replacement).get
						complete(StatusCodes.OK)
					})
				}
			}
		}
	}
}