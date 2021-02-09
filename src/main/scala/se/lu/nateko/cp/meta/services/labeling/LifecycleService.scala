package se.lu.nateko.cp.meta.services.labeling

import java.net.URI

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement

import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.mail.SendMail
import se.lu.nateko.cp.meta.utils.rdf4j._
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

	def updateStatus(station: URI, newStatus: String, newStatusComment: Option[String], user: UserId)(implicit ctxt: ExecutionContext): Try[Unit] = {
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
			writeStatusChange(toStatus, stationUri)
			sendMailOnStatusUpdate(fromStatus, toStatus, newStatusComment, stationUri, user)
		}

	}

	def updateStatusComment(station: URI, newStatusComment: String, user: UserId): Try[Unit] = Try{
		val stationUri = factory.createIRI(station)

		if(!userHasRole(user, Role.TC, stationUri)) throw new UnauthorizedStationUpdateException(s"User does not have TC role")

		val currentCommentStats = getStatements(stationUri, vocab.hasAppStatusComment)
		val newCommentStats = Seq(factory.createStatement(stationUri, vocab.hasAppStatusComment, factory.createLiteral(newStatusComment)))
		server.applyDiff(currentCommentStats, newCommentStats)
	}

	private def getTcUsers(station: IRI): Seq[String] = lookupStationClass(station)
		.flatMap(cls => config.tcUserIds.get(cls.toJava))
		.toSeq
		.flatten
		.map(_.toLowerCase)

	private def userHasRole(user: UserId, role: Role.Role, station: IRI): Boolean = {
		import Role._
		role match{
			case PI =>
				userIsPiOrDeputy(user, station)
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

	private def getStatements(subject: IRI, predicate: IRI): IndexedSeq[Statement] =
		server.getStatements(Some(subject), Some(predicate), None).toIndexedSeq

	private def writeStatusChange(to: AppStatus, station: IRI): Unit = {
		val current = getStatements(station, vocab.hasApplicationStatus) ++ getStatements(station, vocab.hasAppStatusDate)
		val newStats = Seq(
			factory.createStatement(station, vocab.hasApplicationStatus, vocab.lit(to.toString)),
			factory.createStatement(station, vocab.hasAppStatusDate, vocab.lit(java.time.Instant.now()))
		)
		server.applyDiff(current, newStats)
	}

	private def sendMailOnStatusUpdate(
		from: AppStatus, to: AppStatus, statusComment: Option[String],
		station: IRI, user: UserId
	)(implicit ctxt: ExecutionContext): Unit = if(config.mailing.mailSendingActive) Future{

		val stationId = lookupStationId(station).getOrElse("???")

		(from, to) match{
			case (_, AppStatus.submitted) =>
				val recipients: Seq[String] = getTcUsers(station)
				val subject = "Application for labeling received"
				val body = views.html.LabelingEmailSubmitted1(user, stationId).body

				mailer.send(recipients, subject, body, cc = Seq(user.email))

			case (AppStatus.approved, AppStatus.step2ontrack) =>
				val calLabRecipients = if(stationIsAtmospheric(station)) config.calLabEmails else Nil
				val recipients = (getTcUsers(station) :+ config.dgUserId) ++ calLabRecipients
				val subject = s"Labeling Step2 activated for $stationId"
				val body = views.html.LabelingEmailActivated2(user, stationId).body

				mailer.send(recipients, subject, body, cc = Seq(user.email))

			case (_, AppStatus.step2approved) | (_, AppStatus.step2stalled) | (_, AppStatus.step2delayed) if(from != AppStatus.step3approved) =>
				val status: String = to match{
					case AppStatus.step2delayed => "delayed"
					case AppStatus.step2stalled => "stalled"
					case _ => "approved"
				}
				val comment = to match{
					case AppStatus.step2delayed | AppStatus.step2stalled => statusComment
					case _ => None
				}

				val recipients = getStationPiOrDeputyEmails(station)
				val cc = getTcUsers(station) :+ config.riComEmail
				val subject = s"Labeling Step2 ${status} for $stationId"
				val body = views.html.LabelingEmailDecided2(stationId, status, comment).body

				mailer.send(recipients, subject, body, cc)

			case (_, AppStatus.step3approved) =>
				val recipients = getStationPiOrDeputyEmails(station)
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
		val step2ontrack = Value("STEP2ONTRACK")
		val step2delayed = Value("STEP2DELAYED")
		val step2stalled = Value("STEP2STALLED")
		val step2approved = Value("STEP2APPROVED")
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
			step2ontrack -> PI
		),
		rejected -> Map(
			approved -> TC,
			notSubmitted -> TC
		),
		step2ontrack -> Map(
			approved -> TC,
			step2approved -> TC,
			step2stalled -> TC,
			step2delayed -> TC
		),
		step2approved -> Map(
			step2ontrack -> TC,
			step3approved -> DG
		),
		step2stalled -> Map(
			step2ontrack -> TC,
			step2delayed -> TC,
			step2approved -> TC
		),
		step2delayed -> Map(
			step2ontrack -> TC,
			step2stalled -> TC,
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
		case _: NoSuchElementException =>
			val msg = s"Unsupported labeling application status '$name'"
			Failure(new IllegalLabelingStatusException(msg))
	}

}
