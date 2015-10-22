package se.lu.nateko.cp.meta.services

import scala.util.Try
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.LabelingServiceConfig
import se.lu.nateko.cp.meta.ingestion.Vocab
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
import java.nio.charset.StandardCharsets
import se.lu.nateko.cp.meta.LabelingUserDto
import org.openrdf.model.vocabulary.RDF
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsNumber


class StationLabelingService(
	server: InstanceServer,
	provisionalInfoServer: InstanceServer,
	val fileService: FileStorageService,
	conf: LabelingServiceConfig) {

	private val factory = server.factory
	private val vocab = new StationStructuringVocab(factory)

	def getLabelingUserInfo(uinfo: UserInfo): LabelingUserDto = {
		val piUriOpt = provisionalInfoServer
			.getStatements(None, Some(vocab.hasEmail), Some(vocab.lit(uinfo.mail)))
			.collect{case SesameStatement(uri: URI, _, _) => uri}
			.toIndexedSeq.headOption

		piUriOpt match{
			case None =>
				LabelingUserDto(None, uinfo.mail, false, Some(uinfo.givenName), Some(uinfo.surname))
			case Some(piUri) =>
				val props = provisionalInfoServer
					.getStatements(piUri)
					.groupBy(_.getPredicate)
					.map{case (pred, statements) => (pred, statements.head)} //ignoring multiprops
					.collect{case (pred, SesameStatement(_, _, v: Literal)) => (pred, v.getLabel)} //keeping only data props
					.toMap
				LabelingUserDto(
					uri = Some(piUri.toJava),
					mail = uinfo.mail,
					isPi = true,
					firstName = props.get(vocab.hasFirstName).orElse(Some(uinfo.givenName)),
					lastName = props.get(vocab.hasLastName).orElse(Some(uinfo.surname)),
					affiliation = props.get(vocab.hasAffiliation),
					phone = props.get(vocab.hasPhone)
				)
		}
	}

	def saveStationInfo(info: JsObject, uploader: UserInfo): Try[Unit] = Try{

		def extract[T](fieldName: String)(pf: PartialFunction[JsValue, T]): Option[T] =
			info.fields.get(fieldName).collect(pf)

		def getString(fieldName: String) = extract(fieldName){case JsString(str) => str}
		def getDouble(fieldName: String) = extract(fieldName){
			case JsNumber(bigDec) => bigDec.doubleValue
		}
		def getFloat(fieldName: String) = extract(fieldName){
			case JsNumber(bigDec) => bigDec.floatValue
		}
		def getInt(fieldName: String) = extract(fieldName){
			case JsNumber(bigDec) => bigDec.intValue
		}

		val stationUri = getString("stationUri").map(factory.createURI).get

		assertThatWriteIsAuthorized(stationUri, uploader)

		def makeStatement(fieldName: String, lit: Literal) =
			factory.createStatement(stationUri, vocab.getRelative(fieldName), lit)

		def fromString(fieldName: String) = getString(fieldName).map{value => 
			makeStatement(fieldName, vocab.lit(value))
		}
		def fromInt(fieldName: String) =  getInt(fieldName).map{value => 
			makeStatement(fieldName, vocab.lit(value))
		}
		def fromFloat(fieldName: String) =  getFloat(fieldName).map{value => 
			makeStatement(fieldName, vocab.lit(value))
		}
		def fromDouble(fieldName: String) =  getDouble(fieldName).map{value => 
			makeStatement(fieldName, vocab.lit(value))
		}

		val newInfo: Seq[Statement] = Seq(
			fromString("hasShortName"),
			fromString("hasLongName"),
			fromString("hasAddress"),
			fromString("hasWebsite"),
			fromString("hasStationClass"),
			fromDouble("hasLat"),
			fromDouble("hasLon"),
			fromString("hasElevationAboveGround"),
			fromFloat("hasElevationAboveSea"),
			fromString("hasAccessibility"),
			fromString("hasVegetation"),
			fromString("hasAnthropogenics"),
			fromString("hasConstructionStartDate"),
			fromString("hasConstructionEndDate"),
			fromString("hasOperationalDateEstimate"),
			fromString("hasTelecom"),
			fromString("hasExistingInfrastructure"),
			fromInt("hasAnemometerDirection")
		).flatten

		val hasAssociatedFile = vocab.hasAssociatedFile

		val currentInfo = server.getStatements(stationUri).filter{
			case SesameStatement(_, `hasAssociatedFile`, _) => false
			case _ => true
		}
		updateInfo(currentInfo, newInfo, server)
	}

	def saveUserInfo(info: LabelingUserDto, uploader: UserInfo): Try[Unit] = Try{
		if(info.uri.isEmpty) throw new UnauthorizedUserInfoUpdateException("User must be identified by a URI")
		val userUri = factory.createURI(info.uri.get)
		val userEmail = getPiEmails(userUri).toIndexedSeq.headOption.getOrElse(
			throw new UnauthorizedUserInfoUpdateException("User had no email in the database")
		)
		if(!userEmail.equalsIgnoreCase(uploader.mail))
			throw new UnauthorizedUserInfoUpdateException("User is allowed to update only his/her own information")

		def fromString(pred: URI)(str: String) = factory.createStatement(userUri, pred, vocab.lit(str))
		
		val newInfo = Seq(
			info.firstName.map(fromString(vocab.hasFirstName)),
			info.lastName.map(fromString(vocab.hasLastName)),
			info.affiliation.map(fromString(vocab.hasAffiliation)),
			info.phone.map(fromString(vocab.hasPhone))
		).flatten

		val protectedPredicates = Set(vocab.hasEmail, RDF.TYPE)

		val currentInfo = provisionalInfoServer.getStatements(userUri).filter{
			case SesameStatement(_, pred, _) if protectedPredicates.contains(pred) => false
			case _ => true
		}

		updateInfo(currentInfo, newInfo, provisionalInfoServer)
	}

	def processFile(fileInfo: UploadedFile, uploader: UserInfo)(implicit ex: ExecutionContext): Future[Unit] = Future{
		val station = vocab.factory.createURI(fileInfo.station)

		assertThatWriteIsAuthorized(station, uploader)

		val stationUriBytes = fileInfo.station.toString.getBytes(StandardCharsets.UTF_8)
		val hash = fileService.saveAsFile(fileInfo.content, Some(stationUriBytes))
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

		updateInfo(currentInfo, newInfo, server)
	}

	def deleteFile(station: java.net.URI, file: java.net.URI, uploader: UserInfo): Try[Unit] = Try{
		val stationUri: URI = vocab.factory.createURI(station)

		assertThatWriteIsAuthorized(stationUri, uploader)

		val fileUri = vocab.factory.createURI(file)
		server.remove(vocab.factory.createStatement(stationUri, vocab.hasAssociatedFile, fileUri))
	}

	private def updateInfo(currentInfo: Seq[Statement], newInfo: Seq[Statement], server: InstanceServer): Unit = {
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

