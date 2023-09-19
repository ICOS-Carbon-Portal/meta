package se.lu.nateko.cp.meta.metaflow

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Status
import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import se.lu.nateko.cp.meta.MetaUploadConf

trait FileDropMetaSource[T <: TC : TcConf](
	conf: MetaUploadConf
)(using system: ActorSystem) extends MetaUploadService with TriggeredMetaSource[T]:

	export conf.dirName
	final override val directory: Path =
		val dir = Paths.get("metaflowUploads", dirName).toAbsolutePath
		Files.createDirectories(dir)
	final override def log = system.log

	import system.dispatcher
	private var listener: ActorRef = system.deadLetters

	override def registerListener(actor: ActorRef): Unit =
		if(listener != system.deadLetters) listener ! Status.Success
		listener = actor

	override def getTableSink(tableId: String, user: UserId): Try[Sink[ByteString, Future[IOResult]]] =
		if user == conf.uploader then Try:
			val file = getTableFile(tableId)
			FileIO.toPath(file).mapMaterializedValue:
				_.andThen:
					case Success(_) => listener ! 1
					case Failure(exc) => system.log.error(exc, s"Error writing $tcName metadata table $tableId")
		else
			Failure(new UnauthorizedUploadException(s"Only ${conf.uploader} is allowed to upload $tcName metadata"))

	def getTableFile(tableId: String): Path = directory.resolve(tableId)

	private def tcName = summon[TcConf[T]].tcPrefix

end FileDropMetaSource
