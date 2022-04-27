package se.lu.nateko.cp.meta.routes

import scala.language.implicitConversions

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import scala.concurrent.Future
import scala.util.Try
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.UploadDtoReader

object DtoDownloadRoute extends CpmetaJsonProtocol{

	given Unmarshaller[String, Uri] = Unmarshaller(_ => s => Future.fromTry(Try(Uri(s))))

	def apply(uriSer: UriSerializer): Route = {
		val service = new UploadDtoReader(uriSer)

		(get & path("dtodownload")){
			parameter("uri".as[Uri]){uri =>
				service.readDto(uri) match {
					case None => complete(StatusCodes.NotFound)
					case Some(dto) => complete(dto)
				}
			} ~
			complete(StatusCodes.BadRequest -> "Expected a URL as 'uri' query parameter")
		}
	}
}
