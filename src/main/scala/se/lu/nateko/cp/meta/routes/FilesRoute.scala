package se.lu.nateko.cp.meta.routes

import akka.stream.Materializer
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import se.lu.nateko.cp.meta.services.FileStorageService
import akka.http.scaladsl.server.directives.ContentTypeResolver

object FilesRoute {

	def apply(service: FileStorageService): Route =
		path("files" / Segment / Segment){ (hash, fileName) =>
			val contentResolver = implicitly[ContentTypeResolver]
			val contentType = contentResolver(fileName)
			val file = service.getPath(hash).toFile
			getFromFile(file, contentType)
		}

}