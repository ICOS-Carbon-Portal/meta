package se.lu.nateko.cp.meta.services.labeling

import java.net.{URI => JavaURI}

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.LabelingUserDto
import se.lu.nateko.cp.meta.services.UnauthorizedUserInfoUpdateException
import se.lu.nateko.cp.meta.utils.rdf4j.*
import scala.util.Using

trait UserInfoService { self: StationLabelingService =>

	private val userToTcsLookup: Map[String, Seq[JavaURI]] = {
		val userTcPairs = for(
			(tcUri, userMails) <- config.tcUserIds.toSeq;
			userMail <- userMails
		) yield (userMail, tcUri)

		userTcPairs.groupMap(_._1)(_._2)
	}

	def getLabelingUserInfo(uinfo: UserId): LabelingUserDto = {
		val piUriOpt = Using(
			provInfoServer.getStatements(None, Some(vocab.hasEmail), None)
		)(_.collectFirst{
			case Rdf4jStatement(uri: IRI, _, mail)
				if(mail.stringValue.equalsIgnoreCase(uinfo.email)) => uri
		}).get

		val tcs = userToTcsLookup.get(uinfo.email).getOrElse(Nil)
		val isDg: Boolean = config.dgUserId == uinfo.email

		piUriOpt match{
			case None =>
				LabelingUserDto(None, uinfo.email, false, isDg, tcs, None, None)
			case Some(piUri) =>
				val props = provInfoServer
					.getStatements(piUri)
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
		}
	}

	def saveUserInfo(info: LabelingUserDto, uploader: UserId): Unit = {
		if(info.uri.isEmpty) throw new UnauthorizedUserInfoUpdateException("User must be identified by a URI")
		val userUri = factory.createIRI(info.uri.get)
		val userEmail = getPiEmails(userUri).toIndexedSeq.headOption.getOrElse(
			throw new UnauthorizedUserInfoUpdateException("User had no email in the database")
		)
		if(!userEmail.equalsIgnoreCase(uploader.email))
			throw new UnauthorizedUserInfoUpdateException("User is allowed to update only his/her own information")

		def fromString(pred: IRI)(str: String) = factory.createStatement(userUri, pred, vocab.lit(str))

		val newInfo = Seq(
			info.firstName.map(fromString(vocab.hasFirstName)),
			info.lastName.map(fromString(vocab.hasLastName)),
			info.affiliation.map(fromString(vocab.hasAffiliation)),
			info.phone.map(fromString(vocab.hasPhone))
		).flatten

		val protectedPredicates = Set(vocab.hasEmail, RDF.TYPE)

		val currentInfo = provInfoServer.getStatements(userUri).filter{
			case Rdf4jStatement(_, pred, _) if protectedPredicates.contains(pred) => false
			case _ => true
		}

		provInfoServer.applyDiff(currentInfo, newInfo)
	}
}
