package se.lu.nateko.cp.meta.services.labeling

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import org.openrdf.model.URI
import akka.util.ByteString
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.utils.sesame._
import akka.http.scaladsl.model.Multipart


trait FileService { self: StationLabelingService =>

	private val (factory, vocab) = getFactoryAndVocab(server)

	def processFile(upload: Multipart.FormData.Strict, uploader: UserInfo)(implicit ex: ExecutionContext): Future[Unit] = Future{
		val nameToParts = upload.strictParts.map(part => (part.name, part)).toMap
		val fileType = nameToParts("fileType").entity.data.decodeString("UTF-8")
		val stationUri = nameToParts("stationUri").entity.data.decodeString("UTF-8")
		val filePart = nameToParts("uploadedFile")
		val fileName = filePart.filename.get
		val fileContent = filePart.entity.data

		val station = factory.createURI(stationUri)

		assertThatWriteIsAuthorized(station, uploader)

		val stationUriBytes = stationUri.getBytes(StandardCharsets.UTF_8)
		val hash = fileService.saveAsFile(fileContent, Some(stationUriBytes))
		val file = vocab.files.getUri(hash)

		val newInfo = Seq(
			(station, vocab.hasAssociatedFile, file),
			(file, vocab.files.hasName, vocab.lit(fileName)),
			(file, vocab.files.hasType, vocab.lit(fileType))
		).map{
			case (subj, pred, obj) => factory.createStatement(subj, pred, obj)
		}

		val currentInfo = (server.getStatements(Some(station), Some(vocab.hasAssociatedFile), Some(file)) ++
			server.getStatements(Some(file), Some(vocab.files.hasName), None) ++
			server.getStatements(Some(file), Some(vocab.files.hasType), None)).toIndexedSeq

		server.applyDiff(currentInfo, newInfo)
	}

	def deleteFile(station: java.net.URI, file: java.net.URI, uploader: UserInfo): Unit = {
		val stationUri: URI = factory.createURI(station)

		assertThatWriteIsAuthorized(stationUri, uploader)

		val fileUri = factory.createURI(file)
		server.remove(factory.createStatement(stationUri, vocab.hasAssociatedFile, fileUri))
	}


}
