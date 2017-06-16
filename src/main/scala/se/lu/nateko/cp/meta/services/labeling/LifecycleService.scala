package se.lu.nateko.cp.meta.services.labeling

import java.net.URI
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.mail.SendMail
import se.lu.nateko.cp.meta.utils.sesame._
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.services.IllegalLabelingStatusException
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException

import scala.concurrent.{Future, ExecutionContext}
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait LifecycleService { self: StationLabelingService =>

	import LifecycleService._
	import AppStatus.AppStatus

	private val (factory, vocab) = getFactoryAndVocab(server)
	private val mailer = SendMail(config.mailing)

	def updateStatus(station: URI, newStatus: String, user: UserId)(implicit ctxt: ExecutionContext): Try[Unit] = {
		val stationUri = factory.createIRI(station)

		for(
			toStatus <- lookupAppStatus(newStatus);
			fromStatus <- getCurrentStatus(stationUri);
			role <- getRoleForTransition(fromStatus, toStatus);
			_ <- if(userHasRole(user, role, stationUri))
					Success(())
				else
					Failure(new UnauthorizedStationUpdateException(s"User lacks the role of $role"))
		) yield {
			writeStatusChange(fromStatus, toStatus, stationUri)
			sendMailOnStatusUpdate(fromStatus, toStatus, stationUri, user)
		}

	}

	private def getTcUsers(station: IRI): Seq[String] = lookupStationClass(station)
		.flatMap(cls => config.tcUserIds.get(cls))
		.toSeq
		.flatten
		.map(_.toLowerCase)

	private def userHasRole(user: UserId, role: Role.Role, station: IRI): Boolean = {
		import Role._
		role match{
			case PI =>
				userIsPi(user, station)
			case TC =>
				getTcUsers(station).contains(user.email.toLowerCase)
			case DG =>
				config.dgUserId.equalsIgnoreCase(user.email)

			case _ => false
		}
	}

	private def getCurrentStatus(station: IRI): Try[AppStatus] = {
		server.getStringValues(station, vocab.hasApplicationStatus)
			.headOption
			.map(lookupAppStatus)
			.getOrElse(Success(AppStatus.neverSubmitted))
	}

	private def stationIsAtmospheric(station: IRI): Boolean =
		lookupStationClass(station).map(_ == vocab.atmoStationClass).getOrElse(false)

	private def writeStatusChange(from: AppStatus, to: AppStatus, station: IRI): Unit = {
		def toStatements(status: AppStatus) = Seq(factory
			.createStatement(station, vocab.hasApplicationStatus, vocab.lit(status.toString))
		)
		server.applyDiff(toStatements(from), toStatements(to))
	}

	private def sendMailOnStatusUpdate(
		from: AppStatus, to: AppStatus,
		station: IRI, user: UserId
	)(implicit ctxt: ExecutionContext): Unit = if(config.mailing.mailSendingActive) Future{

		val stationId = lookupStationId(station).getOrElse("???")

		(from, to) match{
			case (_, AppStatus.submitted) =>
				val recipients: Seq[String] = getTcUsers(station)
				val subject = "Application for labeling received"
				val body = views.html.LabelingEmailSubmitted1(user, stationId).body

				mailer.send(recipients, subject, body, cc = Seq(user.email))

			case (AppStatus.approved, AppStatus.step2started) =>
				val calLabRecipients = if(stationIsAtmospheric(station)) config.calLabEmails else Nil
				val recipients = (getTcUsers(station) :+ config.dgUserId) ++ calLabRecipients
				val subject = s"Labeling Step2 activated for $stationId"
				val body = views.html.LabelingEmailActivated2(user, stationId).body

				mailer.send(recipients, subject, body, cc = Seq(user.email))

			case (_, AppStatus.step2approved) | (_, AppStatus.step2rejected) if(from != AppStatus.step3approved) =>
				val isRejected = to == AppStatus.step2rejected

				val recipients = getStationPiEmails(station)
				val cc = getTcUsers(station) :+ config.riComEmail
				val subject = s"Labeling Step2 ${if(isRejected) "rejected" else "approved"} for $stationId"
				val body = views.html.LabelingEmailDecided2(stationId, isRejected).body

				mailer.send(recipients, subject, body, cc)

			case (_, AppStatus.step3approved) =>
				val recipients = getStationPiEmails(station)
				val subject = s"Labeling complete for $stationId"
				val body = views.html.LabelingEmailApproved3(stationId).body
				val cc = Seq(config.riComEmail)

				mailer.send(recipients, subject, body, cc)

			case _ => ()
		}
	}
}

object LifecycleService{

	object AppStatus extends Enumeration{
		type AppStatus = Value

		val neverSubmitted = Value("NEVER SUBMITTED")
		val notSubmitted = Value("NOT SUBMITTED")
		val submitted = Value("SUBMITTED")
		val acknowledged = Value("ACKNOWLEDGED")
		val approved = Value("APPROVED")
		val rejected = Value("REJECTED")
		val step2started = Value("STEP2STARTED")
		val step2approved = Value("STEP2APPROVED")
		val step2rejected = Value("STEP2REJECTED")
		val step3approved = Value("STEP3APPROVED")

	}

	object Role extends Enumeration{
		type Role = Value
		val PI = Value("Station PI")
		val TC = Value("ICOS TC representative")
		val DG = Value("ICOS DG")
	}

	import AppStatus._
	import Role._

	val transitions: Map[AppStatus, Map[AppStatus, Role]] = Map(
		neverSubmitted -> Map(submitted -> PI),
		submitted -> Map(acknowledged -> TC),
		acknowledged -> Map(
			notSubmitted -> TC,
			approved -> TC,
			rejected -> TC
		),
		notSubmitted -> Map(
			approved -> TC,
			rejected -> TC,
			submitted -> PI
		),
		approved -> Map(
			rejected -> TC,
			notSubmitted -> TC,
			step2started -> PI
		),
		rejected -> Map(
			approved -> TC,
			notSubmitted -> TC
		),
		step2started -> Map(
			approved -> TC,
			step2approved -> TC,
			step2rejected -> TC
		),
		step2approved -> Map(
			step2started -> TC,
			step2rejected -> TC,
			step3approved -> DG
		),
		step2rejected -> Map(
			step2started -> TC,
			step2approved -> TC
		),
		step3approved -> Map(step2approved -> DG)
	)

	private def getRoleForTransition(from: AppStatus, to: AppStatus): Try[Role] = {
		transitions.get(from) match{

			case None =>
				val message = s"No transitions defined from status: $from"
				Failure(new IllegalLabelingStatusException(message))

			case Some(destinations) =>
				destinations.get(to) match {

					case None =>
						val message = s"No transitions defined from $from to $to"
						Failure(new IllegalLabelingStatusException(message))

					case Some(role) =>
						Success(role)
				}
		}
	}

	private def lookupAppStatus(name: String): Try[AppStatus] = try{
		Success(AppStatus.withName(name))
	} catch{
		case nsee: NoSuchElementException =>
			val msg = s"Unsupported labeling application status '$name'"
			Failure(new IllegalLabelingStatusException(msg))
	}

}
