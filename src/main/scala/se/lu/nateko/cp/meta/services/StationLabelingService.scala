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
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.ingestion.StationStructuringVocab
import org.openrdf.model.Literal


class StationLabelingService(server: InstanceServer, provisionalInfoServer: InstanceServer, conf: LabelingServiceConfig) {

	private val factory = server.factory
	private val vocab = new StationStructuringVocab(factory)


	def saveStationInfo(info: StationLabelingDto, uploader: UserInfo): Try[Unit] = Try{

		val stationUri = factory.createURI(info.stationUri)

		assertThatWriteIsAuthorized(stationUri, uploader)

		def fromString(pred: URI)(value: String): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		def fromInt(pred: URI)(value: Int): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		def fromFloat(pred: URI)(value: Float): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		def fromDouble(pred: URI)(value: Double): Statement =
			factory.createStatement(stationUri, pred, vocab.lit(value))
		
		val newInfo: Seq[Statement] = Seq(
			info.shortName.map(fromString(vocab.hasShortName)),
			info.longName.map(fromString(vocab.hasLongName)),
			info.lat.map(fromDouble(vocab.hasLat)),
			info.lon.map(fromDouble(vocab.hasLon)),
			info.aboveGround.map(fromString(vocab.hasElevationAboveGround)),
			info.aboveSea.map(fromFloat(vocab.hasElevationAboveSea)),
			info.stationClass.map(fromInt(vocab.hasStationClass)),
			info.plannedDateStarting.map(fromString(vocab.hasOperationalDateEstimate))
		).flatten

		val currentInfo = server.getStatements(stationUri)

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


