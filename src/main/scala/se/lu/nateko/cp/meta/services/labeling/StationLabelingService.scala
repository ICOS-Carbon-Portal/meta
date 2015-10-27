package se.lu.nateko.cp.meta.services.labeling

import org.openrdf.model.Literal
import org.openrdf.model.URI

import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.ingestion.StationStructuringVocab
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.services.FileStorageService
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException
import se.lu.nateko.cp.meta.utils.sesame.SesameStatement


class StationLabelingService(
	protected val server: InstanceServer,
	protected val provisionalInfoServer: InstanceServer,
	protected val onto: Onto,
	protected val fileService: FileStorageService
) extends UserInfoService with StationInfoService with FileService {

	private val (_, vocab) = getFactoryAndVocab(provisionalInfoServer)

	protected def getFactoryAndVocab(instServer: InstanceServer) = {
		val factory = instServer.factory
		val vocab = new StationStructuringVocab(factory)
		(factory, vocab)
	}

	protected def assertThatWriteIsAuthorized(stationUri: URI, uploader: UserInfo): Unit = {
		val piEmails = getPis(stationUri).flatMap(getPiEmails).toIndexedSeq

		if(!piEmails.contains(uploader.mail.toLowerCase)) throw new UnauthorizedStationUpdateException(
			"Only the following user(s) is(are) authorized to update this station's info: " +
				piEmails.mkString(" and ")
		)
	}

	protected def getPiEmails(piUri: URI) = provisionalInfoServer
		.getStatements(Some(piUri), Some(vocab.hasEmail), None)
		.collect{
			case SesameStatement(_, _, mail: Literal) => mail.getLabel.toLowerCase
		}

	private def getPis(stationUri: URI) = provisionalInfoServer
		.getStatements(Some(stationUri), Some(vocab.hasPi), None)
		.collect{
			case SesameStatement(_, _, pi: URI) => pi
		}
}

