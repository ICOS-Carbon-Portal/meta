package se.lu.nateko.cp.meta.routes

import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.http.scaladsl.server.{PathMatcher1, Route}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.FileStorageService

object FilesRoute {

	val Sha256Segment: PathMatcher1[Sha256Sum] = Segment.flatMap(Sha256Sum.fromString(_).toOption)

	def apply(service: FileStorageService): Route =
		path("files" / Sha256Segment / Segment){ (hash, fileName) =>
			val contentResolver = implicitly[ContentTypeResolver]
			val contentType = contentResolver(fileName)
			val file = service.getPath(hash).toFile
			getFromFile(file, contentType)
		}

}