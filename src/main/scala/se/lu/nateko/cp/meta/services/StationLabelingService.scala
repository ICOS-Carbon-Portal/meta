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
import se.lu.nateko.cp.meta.Onto


class StationLabelingService(
	server: InstanceServer,
	provisionalInfoServer: InstanceServer,
	onto: Onto,
	val fileService: FileStorageService) {

	private val factory = server.factory
	private val vocab = new StationStructuringVocab(factory)
	private val dataTypeInfos = {
		import org.semanticweb.owlapi.model.IRI
		import se.lu.nateko.cp.meta.ClassDto
		import se.lu.nateko.cp.meta.DataPropertyDto

		def toDatatypeLookup(classInfo: ClassDto) =
			classInfo.properties.collect{
				case DataPropertyDto(prop, _, range) => (prop.uri, range.dataType)
			}.toMap

		val stationClass = onto.factory.getOWLClass(IRI.create(vocab.station.toJava))
		onto.getBottomSubClasses(stationClass)
			.map(onto.getClassInfo)
			.map(classInfo => (classInfo.resource.uri, toDatatypeLookup(classInfo)))
			.toMap
	}

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

		val stationUri = info.fields.get("stationUri")
			.collect{case JsString(str) => str}
			.map(factory.createURI).get

		assertThatWriteIsAuthorized(stationUri, uploader)

		val newInfo: Seq[Statement] = for(
			classUri <- lookupStationClass(stationUri).toSeq;
			(fieldName, fieldValue) <- info.fields.collect{case (name, JsString(value)) => (name, value)};
			propUri = vocab.getRelative(fieldName);
			dataType <- lookupDatatype(classUri.toJava, propUri.toJava).toSeq
		) yield {
			val lit = factory.createLiteral(fieldValue, dataType)
			factory.createStatement(stationUri, propUri, lit)
		}

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

	private def lookupDatatype(classUri: java.net.URI, propUri: java.net.URI): Option[URI] =
		dataTypeInfos.get(classUri).flatMap(_.get(propUri)).map(uri => factory.createURI(uri))

	private def lookupStationClass(stationUri: URI): Option[URI] =
		provisionalInfoServer.getStatements(Some(stationUri), Some(RDF.TYPE), None)
			.map(_.getObject).collect{case uri: URI => uri}.toIndexedSeq.headOption
}

case class UploadedFile(station: java.net.URI, fileName: String, fileType: String, content: ByteString)

