package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.LabelingServiceConfig
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.services.FileStorageService
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer


class StationLabelingService(
	protected val server: InstanceServer,
	protected val provisionalInfoServer: InstanceServer,
	protected val onto: Onto,
	protected val fileStorage: FileStorageService,
	protected val config: LabelingServiceConfig
) extends UserInfoService with StationInfoService with FileService with LifecycleService {

	protected val factory = provisionalInfoServer.factory
	protected val vocab = new StationsVocab(factory)
	protected val protectedPredicates = Set(vocab.hasAssociatedFile, vocab.hasApplicationStatus)
	protected val provRdfLog = provisionalInfoServer match{
		case logging: LoggingInstanceServer => logging.log
		case _ => throw new Exception(
			"Configuration error! Provisional stations metadata InstanceServer is expected to be a LoggingInstanceServer"
		)
	}
	protected val labelingRdfLog = server match{
		case logging: LoggingInstanceServer => logging.log
		case _ => throw new Exception(
			"Configuration error! Labeling metadata InstanceServer is expected to be a LoggingInstanceServer"
		)
	}

	protected def assertThatWriteIsAuthorized(stationUri: IRI, uploader: UserId): Unit =
		if(!userIsPiOrDeputy(uploader, stationUri)) throw new UnauthorizedStationUpdateException(
			"Only PIs are authorized to update station's info"
		)

	protected def userIsPiOrDeputy(user: UserId, station: IRI): Boolean = {
		getStationPiOrDeputyEmails(station).contains(user.email.toLowerCase)
	}

	protected def getPiEmails(piUri: IRI): Seq[String] =
		provisionalInfoServer.getStringValues(piUri, vocab.hasEmail).map(_.toLowerCase)

	protected def lookupStationClass(stationUri: IRI): Option[IRI] =
		InstanceServerUtils.getSingleTypeIfAny(stationUri, provisionalInfoServer)

	protected def lookupStationId(stationUri: IRI): Option[String] =
		provisionalInfoServer.getStringValues(stationUri, vocab.hasShortName).headOption

	protected def getStationPiOrDeputyEmails(stationUri: IRI): Seq[String] = (
		provisionalInfoServer.getUriValues(stationUri, vocab.hasPi) ++
		provisionalInfoServer.getUriValues(stationUri, vocab.hasDeputyPi)
	).flatMap(getPiEmails)
}

