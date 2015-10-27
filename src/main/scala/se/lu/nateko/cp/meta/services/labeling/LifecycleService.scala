package se.lu.nateko.cp.meta.services.labeling

import java.net.URI
import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.utils.sesame._
import org.openrdf.model.Literal
import org.openrdf.model.{URI => SesameUri}
import se.lu.nateko.cp.meta.services.IllegalLabelingStatusException
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException

private object ApplicationStatus{
	val notSubmitted: String = "NOT SUBMITTED"
	val submitted: String = "SUBMITTED"
	val acknowledged: String = "ACKNOWLEDGED"
	val approved: String = "APPROVED"
	val rejected: String = "REJECTED"

	val allStatuses = Set(notSubmitted, submitted, acknowledged, approved, rejected)
}

trait LifecycleService { self: StationLabelingService =>

	import ApplicationStatus._

	private val (factory, vocab) = getFactoryAndVocab(server)

	def updateStatus(station: URI, newStatus: String, uploader: UserInfo): Unit = {

		if(!allStatuses.contains(newStatus))
			throw new IllegalLabelingStatusException(newStatus)

		val stationUri = factory.createURI(station)

		val currentStatus = server.getValues(stationUri, vocab.hasApplicationStatus)
			.collect{case status: Literal => status.getLabel}.headOption

		if(currentStatus.isEmpty || currentStatus.get == notSubmitted)
			assertThatWriteIsAuthorized(stationUri, uploader)
		else assertUserRepresentsTc(stationUri, uploader)

		def toStatement(status: String) = factory
			.createStatement(stationUri, vocab.hasApplicationStatus, vocab.lit(status))

		val currentInfo = currentStatus.map(toStatement).toSeq
		val newInfo = Seq(toStatement(newStatus))

		server.applyDiff(currentInfo, newInfo)
	}

	private def assertUserRepresentsTc(station: SesameUri, uploader: UserInfo): Unit = {
		val tcUsersListOpt = for(
			stationClass <- lookupStationClass(station);
			list <- config.tcUserIds.get(stationClass.toJava)
		) yield list

		if(tcUsersListOpt.isEmpty){
			val message = s"No authorization config found for station (${station.toString}) TC"
			throw new UnauthorizedStationUpdateException(message)
		}

		val authorizedUserIds = tcUsersListOpt.toSeq.flatten.map(_.toLowerCase)

		if(!authorizedUserIds.isEmpty && !authorizedUserIds.contains(uploader.mail.toLowerCase)){
			val message = s"User ${uploader.mail} does not represent station's Thematic Center!"
			throw new UnauthorizedStationUpdateException(message)
		}
	}
}