package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.{CpmetaJsonProtocol, InstOntoServerConfig, ReplaceDto, UpdateDto}
import spray.json.*

import java.net.URI
import scala.language.implicitConversions

class MetadataEntryRouting(authRouting: AuthenticationRouting) extends CpmetaJsonProtocol{

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
				complete(onto.getExposedClasses.map(_.withFallbackBaseUri(instOnto.getWriteContext)))
			} ~
			pathSuffix("listIndividuals"){
				parameter("classUri"){ uriStr =>
					complete(instOnto.getIndividuals(new URI(uriStr)))
				}
			} ~
			pathSuffix("getIndividual"){
				parameter("uri"){ uriStr =>
					complete(instOnto.getIndividual(new URI(uriStr)))
				}
			} ~
			pathSuffix("checkIfUriIsFree"){
				parameter("uri"){ uriStr =>
					complete(JsBoolean(!instOnto.hasIndividual(uriStr)))
				}
			} ~
			pathSuffix("getRangeValues"){
				parameters("classUri", "propUri"){ (classUri, propUri) =>
					complete(instOnto.getRangeValues(new URI(classUri), new URI(propUri)))
				}
			}
		} ~
		post{
			authRouting.allowUsers(authorizedUsers){
				pathSuffix("applyupdates"){
					entity(as[Seq[UpdateDto]])(updates => {
						instOnto.applyUpdates(updates).get
						complete(StatusCodes.OK)
					}) ~
					complete((StatusCodes.BadRequest, "Wrong request payload, expecting an array of update objects"))
				} ~
				pathSuffix("performreplacement"){
					entity(as[ReplaceDto])(replacement => {
						instOnto.performReplacement(replacement).get
						complete(StatusCodes.OK)
					})
				} ~
				pathSuffix("createIndividual"){
					parameters("uri", "typeUri"){ (uriStr, typeUriStr) =>
						instOnto.createIndividual(uriStr, typeUriStr).get
						complete(StatusCodes.OK)
					} ~
					complete((StatusCodes.BadRequest, "Please provide 'uri' and 'typeUri' URL parameters"))
				} ~
				pathSuffix("deleteIndividual"){
					parameter("uri"){ uriStr =>
						instOnto.deleteIndividual(uriStr).get
						complete(StatusCodes.OK)
					} ~
					complete((StatusCodes.BadRequest, "Please provide 'uri' and 'typeUri' URL parameters"))
				}
			}
		}
	}
}
