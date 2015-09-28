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


class StationLabelingService(server: InstanceServer, provisionalInfoServer: InstanceServer, conf: LabelingServiceConfig) {

	private val factory = server.factory
	private val vocab = Vocab(factory)


	def uri(fragment: String) = factory.createURI(StationsIngestion.prefix, fragment)

	def saveStationInfo(info: StationLabelingDto, uploader: UserInfo): Try[Unit] = Try{

		val stationUri = factory.createURI(info.stationUri.toString)

		def fromString(pred: URI)(value: String): Statement =
			factory.createStatement(stationUri, pred, factory.createLiteral(value, XMLSchema.STRING))
		def fromInt(pred: URI)(value: Int): Statement =
			factory.createStatement(stationUri, pred, factory.createLiteral(value.toString, XMLSchema.INTEGER))
		def fromFloat(pred: URI)(value: Float): Statement =
			factory.createStatement(stationUri, pred, factory.createLiteral(value))
		def fromDouble(pred: URI)(value: Double): Statement =
			factory.createStatement(stationUri, pred, factory.createLiteral(value))
		
		val newInfo: Seq[Statement] = Seq(
			info.shortName.map(fromString(uri("hasShortName"))),
			info.longName.map(fromString(uri("hasLongName"))),
			info.lat.map(fromDouble(uri("hasLat"))),
			info.lon.map(fromDouble(uri("hasLon"))),
			info.aboveGround.map(fromString(uri("hasElevationAboveGround"))),
			info.aboveSea.map(fromFloat(uri("hasElevationAboveSea"))),
			info.stationClass.map(fromInt(uri("hasStationClass"))),
			info.plannedDateStarting.map(fromString(uri("hasOperationalDateEstimate")))
		).flatten

		val currentInfo = server.getStatements(stationUri)

		val toRemove = currentInfo.diff(newInfo)
		val toAdd = newInfo.diff(currentInfo)

		server.applyAll(toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true)))
	}

	def writeIsAuthorized(info: StationLabelingDto, uploader: UserInfo): Boolean = {
		true
	}
}


