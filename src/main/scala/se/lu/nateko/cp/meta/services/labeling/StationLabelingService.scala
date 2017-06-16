package se.lu.nateko.cp.meta.services.labeling

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.LabelingServiceConfig
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.services.FileStorageService
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException


class StationLabelingService(
	protected val server: InstanceServer,
	protected val provisionalInfoServer: InstanceServer,
	protected val onto: Onto,
	protected val fileStorage: FileStorageService,
	protected val config: LabelingServiceConfig
) extends UserInfoService with StationInfoService with FileService with LifecycleService {

	private val (_, vocab) = getFactoryAndVocab(provisionalInfoServer)

	protected def getFactoryAndVocab(instServer: InstanceServer) = {
		val factory = instServer.factory
		val vocab = new StationsVocab(factory)
		(factory, vocab)
	}

	protected def assertThatWriteIsAuthorized(stationUri: IRI, uploader: UserId): Unit =
		if(!userIsPi(uploader, stationUri)) throw new UnauthorizedStationUpdateException(
			"Only PIs are authorized to update station's info"
		)

	protected def userIsPi(user: UserId, station: IRI): Boolean = {
		getStationPiEmails(station).contains(user.email.toLowerCase)
	}

	protected def getPiEmails(piUri: IRI): Seq[String] =
		provisionalInfoServer.getStringValues(piUri, vocab.hasEmail).map(_.toLowerCase)

	protected def lookupStationClass(stationUri: IRI): Option[IRI] =
		InstanceServerUtils.getSingleTypeIfAny(stationUri, provisionalInfoServer)

	protected def lookupStationId(stationUri: IRI): Option[String] =
		provisionalInfoServer.getStringValues(stationUri, vocab.hasShortName).headOption

	protected def getStationPiEmails(stationUri: IRI): Seq[String] =
		provisionalInfoServer.getUriValues(stationUri, vocab.hasPi).flatMap(getPiEmails)
}

