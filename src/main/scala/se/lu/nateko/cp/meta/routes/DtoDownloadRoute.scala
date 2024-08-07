package se.lu.nateko.cp.meta.routes

import scala.language.implicitConversions

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.unmarshalling.Unmarshaller
import scala.concurrent.Future
import scala.util.Try
import se.lu.nateko.cp.meta.core.CommonJsonSupport.WithErrors
import se.lu.nateko.cp.meta.CpmetaJsonProtocol
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.UploadDtoReader

object DtoDownloadRoute extends CpmetaJsonProtocol{

	given Unmarshaller[String, Uri] = Unmarshaller(_ => s => Future.fromTry(Try(Uri(s))))

	def apply(uriSer: UriSerializer): Route = {
		val service = new UploadDtoReader(uriSer)

		(get & path("dtodownload")){
			parameter("uri".as[Uri]){uri =>
				val uDtoV = service.readDto(uri)
				uDtoV.result match
					case None => complete(StatusCodes.NotFound -> uDtoV.errors.mkString("\n"))
					case Some(dto) => complete(WithErrors(dto, uDtoV.errors))
			} ~
			complete(StatusCodes.BadRequest -> "Expected a URL as 'uri' query parameter")
		}
	}
}
