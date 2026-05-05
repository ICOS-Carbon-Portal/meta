package se.lu.nateko.cp.meta.metaflow

import scala.language.unsafeNulls

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.event.Logging
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}
import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.MetaUploadConf
import se.lu.nateko.cp.meta.services.UnauthorizedUploadException

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait FileDropMetaSource[T <: TC : TcConf](
	conf: MetaUploadConf
)(using system: ActorSystem) extends MetaUploadService with TriggeredMetaSource[T]:

	private val logger = Logging.getLogger(system, this)

	export conf.dirName
	final override val directory: Path =
		val dir = Paths.get("metaflowUploads", dirName).toAbsolutePath
		Files.createDirectories(dir)

	final override def log = logger

	import system.dispatcher
	private var listener: ActorRef = system.deadLetters

	override def registerListener(actor: ActorRef): Unit =
		if(listener != system.deadLetters) listener ! Status.Success
		listener = actor

	override def getTableSink(tableId: String, user: UserId): Try[Sink[ByteString, Future[IOResult]]] =
		if user.email == conf.uploader then Try:
			val file = getTableFile(tableId)
			FileIO.toPath(file).mapMaterializedValue:
				_.andThen:
					case Success(_) => listener ! 1
					case Failure(exc) => logger.error(exc, s"Error writing $tcName metadata table $tableId")
		else
			Failure(new UnauthorizedUploadException(s"Only ${conf.uploader} is allowed to upload $tcName metadata"))

	def getTableFile(tableId: String): Path = directory.resolve(tableId)

	private def tcName = summon[TcConf[T]].tcPrefix

end FileDropMetaSource
