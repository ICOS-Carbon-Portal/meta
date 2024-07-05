package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.vocabulary.RDF
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.LabelingUserDto
import se.lu.nateko.cp.meta.services.UnauthorizedUserInfoUpdateException
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.{URI => JavaURI}
import scala.util.Using

trait UserInfoService:
	self: StationLabelingService =>

	import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection.getStatements

	private val userToTcsLookup: Map[String, Seq[JavaURI]] = {
		val userTcPairs = for(
			(tcUri, userMails) <- config.tcUserIds.toSeq;
			userMail <- userMails
		) yield (userMail, tcUri)

		userTcPairs.groupMap(_._1)(_._2)
	}

	def getLabelingUserInfo(uinfo: UserId): LabelingUserDto = db.accessProv:
		val piUriOpt = Using(getStatements(null, vocab.hasEmail, null))(
			_.collectFirst:
				case Rdf4jStatement(uri: IRI, _, mail)
					if(mail.stringValue.equalsIgnoreCase(uinfo.email)) => uri
		).get

		val tcs = userToTcsLookup.get(uinfo.email).getOrElse(Nil)
		val isDg: Boolean = config.dgUserId == uinfo.email

		piUriOpt match
			case None =>
				LabelingUserDto(None, uinfo.email, false, isDg, tcs, None, None)
			case Some(piUri) =>
				val props = getStatements(piUri, null, null).toIndexedSeq
					.groupBy(_.getPredicate)
					.map{case (pred, statements) => (pred, statements.head)} //ignoring multiprops
					.collect{case (pred, Rdf4jStatement(_, _, v: Literal)) => (pred, v.getLabel)} //keeping only data props
					.toMap
				LabelingUserDto(
					uri = Some(piUri.toJava),
					mail = uinfo.email,
					isPi = true,
					isDg = isDg,
					tcs = tcs,
					firstName = props.get(vocab.hasFirstName),
					lastName = props.get(vocab.hasLastName),
					affiliation = props.get(vocab.hasAffiliation),
					phone = props.get(vocab.hasPhone)
				)
	end getLabelingUserInfo

	def saveUserInfo(info: LabelingUserDto, uploader: UserId): Unit =
		if(info.uri.isEmpty) throw new UnauthorizedUserInfoUpdateException("User must be identified by a URI")
		val userUri = factory.createIRI(info.uri.get)

		def fromString(pred: IRI)(str: String) = factory.createStatement(userUri, pred, vocab.lit(str))

		val (currentInfo, newInfo) = db.accessProv:
			val userEmail = getPiEmails(userUri).toIndexedSeq.headOption.getOrElse(
				throw new UnauthorizedUserInfoUpdateException("User had no email in the database")
			)
			if(!userEmail.equalsIgnoreCase(uploader.email))
				throw new UnauthorizedUserInfoUpdateException("User is allowed to update only his/her own information")

			val newInfo = Seq(
				info.firstName.map(fromString(vocab.hasFirstName)),
				info.lastName.map(fromString(vocab.hasLastName)),
				info.affiliation.map(fromString(vocab.hasAffiliation)),
				info.phone.map(fromString(vocab.hasPhone))
			).flatten

			val protectedPredicates = Set(vocab.hasEmail, RDF.TYPE)

			val currentInfo = getStatements(userUri).filter{
				case Rdf4jStatement(_, pred, _) if protectedPredicates.contains(pred) => false
				case _ => true
			}.toIndexedSeq

			currentInfo -> newInfo

		db.applyProvDiff(currentInfo, newInfo)
	end saveUserInfo

end UserInfoService
