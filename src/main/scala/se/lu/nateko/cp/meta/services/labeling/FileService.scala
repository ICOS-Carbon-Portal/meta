package se.lu.nateko.cp.meta.services.labeling

import java.nio.charset.StandardCharsets

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import org.openrdf.model.URI

import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.utils.sesame._

case class UploadedFile(station: java.net.URI, fileName: String, fileType: String, content: ByteString)

trait FileService { self: StationLabelingService =>

	private val (factory, vocab) = getFactoryAndVocab(server)

	def processFile(fileInfo: UploadedFile, uploader: UserInfo)(implicit ex: ExecutionContext): Future[Unit] = Future{
		val station = factory.createURI(fileInfo.station)

		assertThatWriteIsAuthorized(station, uploader)

		val stationUriBytes = fileInfo.station.toString.getBytes(StandardCharsets.UTF_8)
		val hash = fileService.saveAsFile(fileInfo.content, Some(stationUriBytes))
		val file = vocab.files.getUri(hash)

		val newInfo = Seq(
			(station, vocab.hasAssociatedFile, file),
			(file, vocab.files.hasName, vocab.lit(fileInfo.fileName)),
			(file, vocab.files.hasType, vocab.lit(fileInfo.fileType))
		).map{
			case (subj, pred, obj) => factory.createStatement(subj, pred, obj)
		}

		val currentInfo = (server.getStatements(Some(station), Some(vocab.hasAssociatedFile), Some(file)) ++
			server.getStatements(Some(file), Some(vocab.files.hasName), None) ++
			server.getStatements(Some(file), Some(vocab.files.hasType), None)).toIndexedSeq

		server.applyDiff(currentInfo, newInfo)
	}

	def deleteFile(station: java.net.URI, file: java.net.URI, uploader: UserInfo): Try[Unit] = Try{
		val stationUri: URI = factory.createURI(station)

		assertThatWriteIsAuthorized(stationUri, uploader)

		val fileUri = factory.createURI(file)
		server.remove(factory.createStatement(stationUri, vocab.hasAssociatedFile, fileUri))
	}


}
