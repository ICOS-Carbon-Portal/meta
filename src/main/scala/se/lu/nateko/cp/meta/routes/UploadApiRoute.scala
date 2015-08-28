package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.UploadMetadataDto
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer

object UploadApiRoute extends CpmetaJsonProtocol{

	def apply(config: CpmetaConfig)(implicit mat: Materializer): Route =
		(post & path("upload") & entity(as[UploadMetadataDto])){umd =>
			complete("OK")
		} ~
		get{
			complete("OK")
		}
	
}