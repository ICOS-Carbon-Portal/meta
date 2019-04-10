package se.lu.nateko.cp.meta.icos

import java.nio.file.Files
import java.nio.file.Paths

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try

import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException

class AtcMetaSource extends TcMetaSource[ATC.type] {

	//private type A = ATC.type

	def state: Source[TcState[ATC.type], () => Unit] = ???

	def getTableSink(tableId: String, user: UserId): Try[Sink[ByteString, Future[IOResult]]] = {
		//TODO Parametrize this when writing final code
		val allowedUser = "uploader@ATC"

		if(user.email == allowedUser) Try{
			val dir = Paths.get("atcmeta").toAbsolutePath
			Files.createDirectories(dir)
			val file = dir.resolve(tableId)
			FileIO.toPath(file)
		} else
			Failure(new UnauthorizedUploadException(s"Only $allowedUser is allowed to upload ATC metadata to CP"))
	}
}
