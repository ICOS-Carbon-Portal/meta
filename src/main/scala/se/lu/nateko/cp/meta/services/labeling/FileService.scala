package se.lu.nateko.cp.meta.services.labeling

import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.{HttpResponse, MediaTypes, Multipart}
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}


trait FileService:
	self: StationLabelingService =>

	import StatementSource.{getStatements, getUriValues, getSingleString}

	def processFile(
		upload: Multipart.FormData.Strict, uploader: UserId
	)(using ExecutionContext): Future[Unit] = Future:

		val (currentInfo, newInfo) = db.accessLbl:
			val nameToParts = upload.strictParts.map(part => (part.name, part)).toMap
			val fileType = nameToParts("fileType").entity.data.decodeString("UTF-8")
			val stationUri = nameToParts("stationUri").entity.data.decodeString("UTF-8")
			val filePart = nameToParts("uploadedFile")
			val fileName = filePart.filename.get
			val fileContent = filePart.entity.data

			val station = factory.createIRI(stationUri)

			assertThatWriteIsAuthorized(station, uploader)(using db.provView)

			val stationUriBytes = stationUri.getBytes(StandardCharsets.UTF_8)
			val hash = fileStorage.saveAsFile(fileContent, Some(stationUriBytes))
			val file = vocab.files.getUri(hash)

			val newInfo = Seq(
				(station, vocab.hasAssociatedFile, file),
				(file, vocab.files.hasName, vocab.lit(fileName)),
				(file, vocab.files.hasType, vocab.lit(fileType))
			).map{
				case (subj, pred, obj) => factory.createStatement(subj, pred, obj)
			}

			val currentInfo = (
				getStatements(station, vocab.hasAssociatedFile, file) ++
				getStatements(file, vocab.files.hasName, null) ++
				getStatements(file, vocab.files.hasType, null)
			).toIndexedSeq
			currentInfo -> newInfo

		db.applyLblDiff(currentInfo, newInfo)
	end processFile

	def deleteFile(station: URI, file: URI, uploader: UserId): Unit =
		val statIri = station.toRdf
		db.accessProv:
			assertThatWriteIsAuthorized(statIri, uploader)
		val st = factory.createStatement(statIri, vocab.hasAssociatedFile, file.toRdf)
		db.applyLblDiff(Seq(st), Seq.empty)


	def getFilePack(stationId: URI): HttpResponse =

		val fileHashesAndNames: IndexedSeq[(Sha256Sum, String)] = db.accessLbl:
			getUriValues(stationId.toRdf, vocab.hasAssociatedFile).map: fileUri =>
				val hash = vocab.files.getFileHash(fileUri)
				val fileName = getSingleString(fileUri, vocab.files.hasName)
					.getOrThrow(new MetadataException(_))
				(hash, fileName)
		val source = fileStorage.getZipSource(fileHashesAndNames)
		val entity = Chunked.fromData(MediaTypes.`application/zip`, source)
		HttpResponse(entity = entity)

end FileService
