package se.lu.nateko.cp.meta.services.labeling

import scala.util.Try

import org.openrdf.model.Literal
import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF

import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.LabelingUserDto
import se.lu.nateko.cp.meta.services.UnauthorizedUserInfoUpdateException
import se.lu.nateko.cp.meta.utils.sesame._

trait UserInfoService { self: StationLabelingService =>

	private val (factory, vocab) = getFactoryAndVocab(provisionalInfoServer)

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

		provisionalInfoServer.applyDiff(currentInfo, newInfo)
	}
}
