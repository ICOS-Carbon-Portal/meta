package se.lu.nateko.cp.meta.services.labeling

import java.net.URI
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.mail.SendMail
import se.lu.nateko.cp.meta.utils.sesame._
import org.openrdf.model.Literal
import org.openrdf.model.{URI => SesameUri}
import se.lu.nateko.cp.meta.services.IllegalLabelingStatusException
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException

import scala.concurrent.{Future, ExecutionContext}

trait LifecycleService { self: StationLabelingService =>

	import LifecycleService._

	private val (factory, vocab) = getFactoryAndVocab(server)
	private val mailer = SendMail(config.mailing)

	def updateStatus(station: URI, newStatus: String, user: UserInfo)(implicit ctxt: ExecutionContext): Unit = {

		ensureStatusIsLegal(newStatus)

		val stationUri = factory.createURI(station)

		val currentStatus = server.getValues(stationUri, vocab.hasApplicationStatus)
			.collect{case status: Literal => status.getLabel}.headOption

		ensureStatusChangeIsLegal(currentStatus, newStatus)

		if(needsPiPermissions(newStatus)) assertThatWriteIsAuthorized(stationUri, user)
		if(needsTcPermissions(newStatus)) assertUserRepresentsTc(stationUri, user)

		def toStatement(status: String) = factory
			.createStatement(stationUri, vocab.hasApplicationStatus, vocab.lit(status))

		val currentInfo = currentStatus.map(toStatement).toSeq
		val newInfo = Seq(toStatement(newStatus))

		server.applyDiff(currentInfo, newInfo)

		if(newStatus == submitted) Future{
			val recipients: Seq[String] = lookupStationClass(factory.createURI(station))
					.flatMap(cls => config.tcUserIds.get(cls))
					.toSeq
					.flatten

			if(recipients.nonEmpty){
				val subject = "Application for labeling received"
				val templatePath = config.mailing.templatePaths.submitted
				val body = SendMail.getBody(templatePath, Map(
					"givenName" -> user.givenName,
					"surname" -> user.surname,
					"stationId" -> lookupStationId(stationUri).getOrElse("???")
				))

				mailer.send(recipients, subject, body, true, Seq(user.mail))
			}
		}
	}

	private def assertUserRepresentsTc(station: SesameUri, user: UserInfo): Unit = {
		val tcUsersListOpt = for(
			stationClass <- lookupStationClass(station);
			list <- config.tcUserIds.get(stationClass)
		) yield list

		ensureUserRepresentsTc(tcUsersListOpt, user, station)
	}
}

object LifecycleService{
	val notSubmitted: String = "NOT SUBMITTED"
	val submitted: String = "SUBMITTED"
	val acknowledged: String = "ACKNOWLEDGED"
	val approved: String = "APPROVED"
	val rejected: String = "REJECTED"

	private val allStatuses = Set(notSubmitted, submitted, acknowledged, approved, rejected)
	private val wereSubmitted = Set(submitted, acknowledged, approved, rejected)

	private def afterSubmission(statuses: String*) = statuses.forall(wereSubmitted.contains)

	def userRepresentsTc(authorizedUserIds: Seq[String], user: UserInfo): Boolean =
		// If no user is authorised, then everyone is authorised
		authorizedUserIds.isEmpty ||
		authorizedUserIds.map(_.toLowerCase).contains(user.mail.toLowerCase)

	private def statusChangeIsLegal(currentStatus: Option[String], newStatus: String): Boolean =
		(currentStatus, newStatus) match {
			case (None, `submitted`) => true
			case (Some(`notSubmitted`), `submitted`) => true
			case (Some(from), `submitted`) if afterSubmission(from) => false
			case (Some(from), to) if afterSubmission(from, to) => true
			case (Some(from), `notSubmitted`) if afterSubmission(from) => true
			case _ => false
		}

	def needsPiPermissions(newStatus: String): Boolean = newStatus == submitted
	def needsTcPermissions(newStatus: String): Boolean = newStatus != submitted

	def ensureStatusChangeIsLegal(currentStatus: Option[String], newStatus: String): Unit =
		if(!statusChangeIsLegal(currentStatus, newStatus)) {
			val status = currentStatus.getOrElse("(Nothing)")
			val message = s"Changing application status from $status to $newStatus is illegal"
			throw new IllegalLabelingStatusException(message)
		}

	def ensureStatusIsLegal(newStatus: String): Unit =
		if(!allStatuses.contains(newStatus)){
			val msg = s"Unsupported labeling application status '$newStatus'"
			throw new IllegalLabelingStatusException(msg)
		}

	def ensureUserRepresentsTc(tcUsersListOpt: Option[Seq[String]], user: UserInfo, station: SesameUri): Unit = {
		if(tcUsersListOpt.isEmpty){
			val message = s"No authorization config found for station (${station}) TC"
			throw new UnauthorizedStationUpdateException(message)
		}

		val authorizedUserIds = tcUsersListOpt.getOrElse(Nil)

		if(!userRepresentsTc(authorizedUserIds, user)){
			val message = s"User ${user.mail} does not represent station's Thematic Center!"
			throw new UnauthorizedStationUpdateException(message)
		}
	}
}
