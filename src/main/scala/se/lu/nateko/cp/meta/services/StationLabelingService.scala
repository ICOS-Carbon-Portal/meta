package se.lu.nateko.cp.meta.services

import scala.util.Try
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.LabelingServiceConfig
import se.lu.nateko.cp.meta.ingestion.Vocab
import se.lu.nateko.cp.meta.StationLabelingDto
import org.openrdf.model.URI
import org.openrdf.model.Statement
import org.openrdf.model.vocabulary.XMLSchema
import se.lu.nateko.cp.meta.ingestion.StationsIngestion
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.ingestion.StationStructuringVocab
import org.openrdf.model.Literal
import akka.stream.scaladsl.Sink
import akka.http.scaladsl.model.Multipart
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.openrdf.model.Value


class StationLabelingService(
	server: InstanceServer,
	provisionalInfoServer: InstanceServer,
	val fileService: FileStorageService,
	conf: LabelingServiceConfig) {

	private val factory = server.factory
	private val vocab = new StationStructuringVocab(factory)


	def saveStationInfo(info: StationLabelingDto, uploader: UserInfo): Try[Unit] = Try{

		val stationUri = factory.createURI(info.stationUri)

		assertThatWriteIsAuthorized(stationUri, uploader)

		def fromString(pred: URI)(value: String): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		def fromInt(pred: URI)(value: Int): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		def fromFloat(pred: URI)(value: Float): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		def fromDouble(pred: URI)(value: Double): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))

		val newInfo: Seq[Statement] = Seq(
			info.shortName.map(fromString(vocab.hasShortName)),
			info.longName.map(fromString(vocab.hasLongName)),
			info.address.map(fromString(vocab.hasAddress)),
			info.website.map(fromString(vocab.hasWebsite)),
			info.stationClass.map(fromString(vocab.hasStationClass)),
			info.lat.map(fromDouble(vocab.hasLat)),
			info.lon.map(fromDouble(vocab.hasLon)),
			info.aboveGround.map(fromString(vocab.hasElevationAboveGround)),
			info.aboveSea.map(fromFloat(vocab.hasElevationAboveSea)),
			info.accessibility.map(fromString(vocab.hasAccessibility)),
			info.vegetation.map(fromString(vocab.hasVegetation)),
			info.anthropogenics.map(fromString(vocab.hasAnthropogenics)),
			info.constructionStartDate.map(fromString(vocab.hasConstructionStartDate)),
			info.constructionEndDate.map(fromString(vocab.hasConstructionEndDate)),
			info.plannedDateOperational.map(fromString(vocab.hasOperationalDateEstimate)),
			info.telecom.map(fromString(vocab.hasTelecom)),
			info.infrastructure.map(fromString(vocab.hasExistingInfrastructure))
		).flatten

		val currentInfo = server.getStatements(stationUri).filter{
			case SesameStatement(_, pred: URI, _) if pred == vocab.hasAssociatedFile => false
			case _ => true
		}
		updateInfo(currentInfo, newInfo)
	}

	def processFile(fileInfo: UploadedFile, uploader: UserInfo)(implicit ex: ExecutionContext): Future[Unit] = Future{
		val station = vocab.factory.createURI(fileInfo.station)

		assertThatWriteIsAuthorized(station, uploader)

		val hash = fileService.saveAsFile(fileInfo.content)
		val file = vocab.files.getUri(hash)

		val newInfo = Seq(
			(station, vocab.hasAssociatedFile, file),
			(file, vocab.files.hasName, vocab.lit(fileInfo.fileName)),
			(file, vocab.files.hasType, vocab.lit(fileInfo.fileType))
		).map{
			case (subj, pred, obj) => vocab.factory.createStatement(subj, pred, obj)
		}

		val currentInfo = (server.getStatements(Some(station), Some(vocab.hasAssociatedFile), Some(file)) ++
			server.getStatements(Some(file), Some(vocab.files.hasName), None) ++
			server.getStatements(Some(file), Some(vocab.files.hasType), None)).toIndexedSeq

		updateInfo(currentInfo, newInfo)
	}

	def deleteFile(station: java.net.URI, file: java.net.URI, uploader: UserInfo): Try[Unit] = Try{
		val stationUri: URI = vocab.factory.createURI(station)

		assertThatWriteIsAuthorized(stationUri, uploader)

		val fileUri = vocab.factory.createURI(file)
		server.remove(vocab.factory.createStatement(stationUri, vocab.hasAssociatedFile, fileUri))
	}

	private def updateInfo(currentInfo: Seq[Statement], newInfo: Seq[Statement]): Unit = {
		val toRemove = currentInfo.diff(newInfo)
		val toAdd = newInfo.diff(currentInfo)

		server.applyAll(toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true)))
	}

	private def assertThatWriteIsAuthorized(stationUri: URI, uploader: UserInfo): Unit = {
		val piEmails = getPis(stationUri).flatMap(getPiEmails).toIndexedSeq

		if(!piEmails.contains(uploader.mail.toLowerCase)) throw new UnauthorizedStationUpdateException(
			"Only the following user(s) is(are) authorized to update this station's info: " +
				piEmails.mkString(" and ")
		)
	}

	private def getPis(stationUri: URI) = provisionalInfoServer
		.getStatements(Some(stationUri), Some(vocab.hasPi), None)
		.collect{
			case SesameStatement(_, _, pi: URI) => pi
		}

	private def getPiEmails(piUri: URI) = provisionalInfoServer
		.getStatements(Some(piUri), Some(vocab.hasEmail), None)
		.collect{
			case SesameStatement(_, _, mail: Literal) => mail.getLabel.toLowerCase
		}
}

case class UploadedFile(station: java.net.URI, fileName: String, fileType: String, content: ByteString)

