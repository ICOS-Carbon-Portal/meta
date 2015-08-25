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

object MetadataEntryApiRoute extends CpmetaJsonProtocol{

	def apply(instOnto: InstOnto)(implicit mat: Materializer): Route = {
		val onto = instOnto.onto
		get{
			pathPrefix("api"){
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
			}
		} ~
		post{
			pathPrefix("api"){
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